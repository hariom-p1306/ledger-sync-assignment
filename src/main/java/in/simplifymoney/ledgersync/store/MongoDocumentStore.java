package in.simplifymoney.ledgersync.store;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.Decimal128;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.addEachToSet;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.setOnInsert;

/**
 * MongoDB implementation. Each document is a canonical financial event; its
 * `_id` is derived from financial facts, while `source_message_ids` is an
 * evidence array. This makes a retry/upsert idempotent without treating an
 * upload ID as the transaction identity.
 */
public final class MongoDocumentStore implements DocumentStore, AutoCloseable {
    private final MongoClient client;
    private final MongoCollection<Document> transactions;

    public MongoDocumentStore(String connectionString, String database) {
        this.client = MongoClients.create(connectionString);
        MongoDatabase db = client.getDatabase(database);
        this.transactions = db.getCollection("transactions");
        transactions.createIndex(Indexes.compoundIndex(Indexes.ascending("account_last4", "month"),
                Indexes.descending("occurred_at")));
        transactions.createIndex(Indexes.ascending("source_message_ids"));
        transactions.createIndex(Indexes.compoundIndex(Indexes.ascending("account_last4", "category")));
    }

    @Override
    public void save(NormalizedTxn txn) {
        String id = TransactionKeys.financial(txn);
        transactions.updateOne(eq("_id", id), combine(
                setOnInsert("account_last4", txn.accountLast4()),
                setOnInsert("occurred_at", txn.occurredAt().toString()),
                setOnInsert("month", YearMonth.from(txn.occurredAt()).toString()),
                setOnInsert("direction", txn.direction().name()),
                setOnInsert("amount", txn.amount()),
                setOnInsert("category", txn.category().name()),
                setOnInsert("merchant", txn.merchant()),
                addEachToSet("source_message_ids", txn.sourceMessageIds())), new UpdateOptions().upsert(true));
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        return transactions.find(and(eq("account_last4", accountLast4), eq("month", month.toString())))
                .sort(descending("occurred_at")).map(MongoDocumentStore::transaction).into(new java.util.ArrayList<>());
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> totals = new LinkedHashMap<>();
        for (Category category : Category.values()) totals.put(category, BigDecimal.ZERO.setScale(2));
        for (Document row : transactions.aggregate(List.of(match(eq("account_last4", accountLast4)),
                group("$category", sum("total", "$amount"))))) {
            totals.put(Category.valueOf(row.getString("_id")), decimal(row.get("total")).setScale(2));
        }
        return totals;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        Document found = transactions.find(eq("source_message_ids", messageId)).first();
        return found == null ? Optional.empty() : Optional.of(transaction(found));
    }

    @Override
    public List<NormalizedTxn> all() {
        return transactions.find().sort(com.mongodb.client.model.Sorts.ascending("occurred_at"))
                .map(MongoDocumentStore::transaction).into(new java.util.ArrayList<>());
    }

    @Override public void close() { client.close(); }

    private static Document document(NormalizedTxn t) {
        return new Document("account_last4", t.accountLast4())
                .append("occurred_at", t.occurredAt().toString())
                .append("month", YearMonth.from(t.occurredAt()).toString())
                .append("direction", t.direction().name())
                .append("amount", t.amount())
                .append("category", t.category().name())
                .append("merchant", t.merchant())
                .append("source_message_ids", t.sourceMessageIds());
    }

    @SuppressWarnings("unchecked")
    private static NormalizedTxn transaction(Document d) {
        List<String> evidence = ((List<Object>) d.get("source_message_ids")).stream().map(String::valueOf).sorted().toList();
        return new NormalizedTxn(d.getString("account_last4"), OffsetDateTime.parse(d.getString("occurred_at")),
                Direction.valueOf(d.getString("direction")), decimal(d.get("amount")).setScale(2),
                Category.valueOf(d.getString("category")), d.getString("merchant"), evidence);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Decimal128 decimal128) return decimal128.bigDecimalValue();
        if (value instanceof BigDecimal bigDecimal) return bigDecimal;
        throw new IllegalStateException("Mongo amount is not Decimal128: " + value);
    }
}
