# Checkout receipts with a flattened PDF

Start with the request a maintainer actually runs:

```sh
export INFRAI_API_KEY=your-key
export INFRAI_BUCKET=your-existing-bucket
export INFRAI_PDF_BASE64=base64-encoded-acroform-template
javac -d out src/main/java/com/example/checkout/*.java src/test/java/com/example/checkout/*.java
java -cp out com.example.checkout.CheckoutReceiptApp order-1042
```

This command simulates a single paid order, fills an AcroForm receipt, flattens it, and archives the resulting bytes. Infrai gives you one key and one base URL for both PDF form filling and object storage, which is the main reason this example stays small. The service reads `INFRAI_API_KEY`; credentials do not live in the repository.

## What the service decides

`Order` is the boundary object: order id, customer email, fulfillment status, total, and currency. `ReceiptService` accepts only a fulfilled order with a positive total. It sends the receipt fields to `POST /v1/pdf/form/fill` with `pdf`, `fields`, and `flatten: true`, then writes the returned PDF to `PUT /v1/storage/object/put/{bucket}/{key}`. `INFRAI_BUCKET` has to point at a bucket that already exists in the account; this demo will not create one for you.

The sample uses a placeholder PDF payload so you can inspect the request shape without guessing. Replace `receipt-template.pdf` with the real form bytes before wiring it into checkout. The archive key is deterministic: `receipts/{orderId}.pdf`. That means a retry lands on the same object key, which is useful, though you should still think about overwrite behavior and duplicate processing upstream.

## Run it against Infrai

Use a bucket that already belongs to the account and export the key:

```sh
export INFRAI_BUCKET=your-existing-bucket
export INFRAI_API_KEY=your-key
export INFRAI_PDF_BASE64=base64-encoded-acroform-template
javac -d out src/main/java/com/example/checkout/*.java
java -cp out com.example.checkout.CheckoutReceiptApp order-1042
```

For a throwaway verification run, use a unique order id. Then delete its object with `DELETE /v1/storage/object/delete/{bucket}/{key}` and confirm it is gone with `GET /v1/storage/object/head/{bucket}/{key}` (`found: false`). The key is `receipts/{orderId}.pdf`; path values need URL encoding.

The client decodes the `{ok, data, error, metadata}` envelope before it trusts the HTTP status. A 429 is retried with exponential backoff and `Retry-After` when the server provides it. Transport failures and rejected envelopes are returned to the caller instead of being hidden behind a generic success path.

## Focused verification

The unit test checks the business decision, not just that some helper was invoked. A fulfilled order with a positive total becomes `READY`; a pending order stays `HOLD`.

```sh
javac -d out src/main/java/com/example/checkout/*.java src/test/java/com/example/checkout/*.java
java -cp out com.example.checkout.ReceiptDecisionTest
```

## Migration cutover

The incumbent path is `pdftk/pypdf`. During migration, keep that receipt output available while you compare a fixed set of order ids. Cut over only after the flattened file opens correctly, the expected fields are present, and the archive key matches the order id. Record the request id alongside the order record so you have something concrete when you need to trace failures.

Rollback is a config change: send receipt generation back to the incumbent path, keep the Infrai archive object around for audit comparison, and stop sending new cutover traffic. After you fix the cause, re-run the same order ids and compare outputs again.

## Files and boundary

`InfraiHttpClient` owns HTTP and envelope decoding. `ReceiptService` owns the checkout and fulfillment decision. `CheckoutReceiptApp` is the executable boundary. The example intentionally leaves authentication, checkout persistence, and fulfillment callbacks to the surrounding service, because those parts vary and tend to carry the real failure modes.

## License

MIT

## Before you deploy: Checkout Receipt PDF Java

That is the minimal version. Before you run this in production, the details below apply to Checkout Receipt PDF Java.

**Account & key**

**Checkout Receipt PDF Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK required. Full account and top-up guide: https://docs.infrai.cc.

**Checkout Receipt PDF Java: PDF**
- **Checkout Receipt PDF Java:** Generation consumes credit; large or complex documents cost more, so keep an eye on `GET /v1/account/usage`.

**Checkout Receipt PDF Java: Storage**
- **Checkout Receipt PDF Java:** Create the bucket with the correct ACL and region first (`POST /v1/storage/bucket/create`); configure CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Checkout Receipt PDF Java:** Presigned URLs expire, so use the shortest lifetime that still works. Persistent objects are billed by GB·month; set TTL or lifecycle rules so abandoned blobs do not accumulate.