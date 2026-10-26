# Mini Shop

This is a plain Java Maven project. You do not need Spring, a database, Docker,
a web server or an internet service. The repository and notification interfaces
represent external dependencies.

## Getting started

1. Extract the archive and open its `pom.xml` in IntelliJ IDEA as a Maven project.
2. Select JDK 17 as the project SDK and Maven runner JDK.
3. Allow Maven to download the dependencies. The first build needs internet access.
4. Run the following commands from the directory containing `pom.xml`.

Windows PowerShell:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd test
```

Linux or macOS:

```bash
chmod +x mvnw
./mvnw -version
./mvnw test
```

The Maven wrapper downloads Maven automatically; a separate Maven installation
is not required. `JAVA_HOME`, if set, must point to your JDK.

## Application behavior

All monetary values are integer cents. For example, 1,250 means 12.50 currency
units. Keep calculations and assertions in cents.

### PriceCalculator

`calculateTotal(unitPriceCents, quantity, premiumCustomer)` calculates an order
containing a single product type.

| Rule | Required behavior |
| --- | --- |
| Unit price | Inclusive range 1 through 1,000,000 cents |
| Quantity | Inclusive range 1 through 100 |
| Invalid price or quantity | Throw `IllegalArgumentException` |
| Subtotal | Unit price multiplied by quantity |
| Standard customer | No discount |
| Premium customer | Pay 90% of the subtotal, rounded down to whole cents |
| Shipping | Add 500 cents if the subtotal **after discount** is below 5,000 cents |
| Free shipping | Subtotal after discount is at least 5,000 cents |
| Total | Discounted subtotal plus shipping |

### Product

`Product` is an immutable Java record. Its code must not be null, empty or blank.
Its price uses the same range as `PriceCalculator`. Available quantity must be
zero or positive. Invalid constructor arguments throw `IllegalArgumentException`.
Record accessors are `code()`, `unitPriceCents()` and `availableQuantity()`.

### OrderService

`placeOrder(customerId, productCode, quantity, premiumCustomer)` follows these rules:

1. Validate customer ID, product code and quantity, in that order. IDs and codes
   must not be null, empty or blank. Quantity must be between 1 and 100 inclusive.
   Invalid input throws `IllegalArgumentException` before contacting dependencies.
2. Look up the product by its exact code once. IDs and codes are used as supplied;
   they are not trimmed or converted to another case.
3. If the product is missing, throw `ProductNotFoundException`.
4. If the requested quantity exceeds available stock, throw
   `InsufficientStockException`. Requesting exactly the available quantity is allowed.
5. Calculate the total using the real `PriceCalculator`.
6. Save the order once, then send its confirmation once, using the same order.
7. Return the order, containing the supplied customer ID, product code, quantity
   and calculated total.

No order is saved or confirmation sent for validation, missing-product or stock
errors. If saving throws `IllegalStateException`, propagate that exception and do
not send a confirmation. If notification throws `IllegalStateException`, propagate
it; saving has already happened and is not rolled back.

## Running your tests

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd test "-Dtest=PriceCalculatorTest"
.\mvnw.cmd test "-Dtest=OrderServiceTest"
.\mvnw.cmd clean verify
```

On Linux/macOS, replace `.\mvnw.cmd` with `./mvnw`. Test class names should end
with `Test` so Maven discovers them. You can also use IntelliJ's JUnit runner.
The ordinary `test` command runs tests independently of the static-analysis gate.
The `static-analysis` profile enables that gate for the final verification.
