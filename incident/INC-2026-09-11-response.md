The HDFC parser treated the available balance as the transaction amount when the actual amount was written as a whole rupee value such as `Rs.5`.
We reproduced it with the incident-shaped message and traced it to a generic whole-document amount matcher that required two decimal places.
Any supported bank alert with a whole-number transaction amount followed by a decimal balance or limit could be affected; the merchant was irrelevant.
Parsers now capture the amount adjacent to their debit, credit, sent, received, or card-spend verb, and normalize that capture to two decimal places.
The new regression test fails under the old extraction rule and verifies that ₹5.00 and the ₹92,213.10 stated balance remain distinct.
