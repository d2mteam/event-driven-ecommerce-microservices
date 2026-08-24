import crypto from "node:crypto";
import http from "node:http";

const port = Number(process.env.PORT ?? 8090);
const tmnCode = process.env.VNPAY_TMN_CODE ?? "DEMOV210";
const hashSecret = process.env.VNPAY_HASH_SECRET ?? "local-demo-secret";
const merchantIpnUrl = process.env.MERCHANT_IPN_URL
  ?? "http://host.docker.internal:8080/api/payments/vnpay/ipn";

const refunds = new Map();
const transactionNumbers = new Map();

function formEncode(value) {
  return encodeURIComponent(value)
    .replaceAll("%20", "+")
    .replaceAll("!", "%21")
    .replaceAll("'", "%27")
    .replaceAll("(", "%28")
    .replaceAll(")", "%29")
    .replaceAll("~", "%7E");
}

function canonicalize(parameters) {
  return Object.entries(parameters)
    .filter(([key, value]) => key.startsWith("vnp_")
      && key !== "vnp_SecureHash"
      && key !== "vnp_SecureHashType"
      && value !== undefined
      && value !== null
      && String(value).trim() !== "")
    .sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([key, value]) => `${formEncode(key)}=${formEncode(String(value))}`)
    .join("&");
}

function hmac(data) {
  return crypto.createHmac("sha512", hashSecret).update(data, "utf8").digest("hex");
}

function validHash(data, received) {
  if (!received || !/^[0-9a-fA-F]{128}$/.test(received)) return false;
  return crypto.timingSafeEqual(
    Buffer.from(hmac(data), "hex"),
    Buffer.from(received, "hex"),
  );
}

function queryObject(searchParams) {
  return Object.fromEntries(searchParams.entries());
}

function signedQuery(parameters) {
  return `${canonicalize(parameters)}&vnp_SecureHash=${hmac(canonicalize(parameters))}`;
}

function nowVnpay() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}${value.month}${value.day}${value.hour}${value.minute}${value.second}`;
}

function transactionNo(txnRef) {
  if (!transactionNumbers.has(txnRef)) {
    transactionNumbers.set(
      txnRef,
      `${Date.now()}`.slice(-14).padStart(14, "0"),
    );
  }
  return transactionNumbers.get(txnRef);
}

function callbackParameters(payment, result, bankCode, paymentMethod) {
  const outcomes = {
    success: ["00", "00"],
    failed: ["51", "02"],
    cancel: ["24", "02"],
  };
  const [responseCode, status] = outcomes[result] ?? outcomes.failed;
  return {
    vnp_Amount: payment.vnp_Amount,
    vnp_BankCode: bankCode || "NCB",
    vnp_BankTranNo: `MOCK${transactionNo(payment.vnp_TxnRef)}`,
    vnp_CardType: paymentMethod === "qr" ? "QRCODE" : "ATM",
    vnp_OrderInfo: payment.vnp_OrderInfo,
    vnp_PayDate: nowVnpay(),
    vnp_ResponseCode: responseCode,
    vnp_TmnCode: tmnCode,
    vnp_TransactionNo: transactionNo(payment.vnp_TxnRef),
    vnp_TransactionStatus: status,
    vnp_TxnRef: payment.vnp_TxnRef,
  };
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function paymentPage(payment, originalQuery) {
  const action = `/complete?${originalQuery}`;
  const amount = new Intl.NumberFormat("vi-VN").format(
    Number(payment.vnp_Amount) / 100,
  );
  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Cổng thanh toán VNPAY Local</title>
  <style>
    :root{font-family:Arial,sans-serif;color:#213547;background:#f3f6f9}
    *{box-sizing:border-box}body{margin:0;min-height:100vh}.topbar{height:5px;background:linear-gradient(90deg,#075ea9 0 67%,#e51d35 67%)}
    header{height:72px;background:#fff;border-bottom:1px solid #dce5ed;display:flex;align-items:center;justify-content:space-between;padding:0 max(24px,calc((100vw - 1060px)/2))}
    .brand{font-size:25px;font-weight:800;font-style:italic;color:#075ea9}.brand span{color:#e51d35}.safe{font-size:13px;color:#637789}
    main{width:min(1060px,calc(100% - 32px));margin:32px auto;display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:24px}
    .panel{background:#fff;border:1px solid #dce5ed;border-radius:10px;box-shadow:0 8px 24px #25476212}.content{padding:28px 32px}
    .local{margin-bottom:20px;padding:11px 14px;border:1px solid #f1d480;background:#fff9df;border-radius:7px;color:#775b00;font-size:13px}
    h1{font-size:23px;margin:0 0 6px}h2{font-size:17px;margin:26px 0 13px}p{line-height:1.55;margin:5px 0;color:#607385}
    .methods{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.method{position:relative;border:1px solid #cbd8e3;border-radius:8px;padding:16px;cursor:pointer;background:#fff}
    .method:has(input:checked){border:2px solid #0876b9;padding:15px;background:#f5fbff}.method input{position:absolute;opacity:0}.method strong{display:block;margin-bottom:5px}.method small{color:#6c7e8d}
    .fields{display:grid;grid-template-columns:1fr 1fr;gap:14px}.field{display:grid;gap:7px}.field--wide{grid-column:1/-1}label{font-size:13px;font-weight:700;color:#42586a}
    input,select{width:100%;border:1px solid #b9c9d6;border-radius:7px;padding:12px 13px;font:inherit;background:#fff;color:#213547}input:focus,select:focus{outline:2px solid #8ac8ef;border-color:#0876b9}
    .hint{background:#eef7fd;border-radius:7px;padding:12px 14px;margin-top:14px;font-size:13px;color:#456277}.hint code{font-weight:700;color:#075ea9}
    .actions{display:flex;align-items:center;gap:14px;margin-top:24px}.button{border:0;border-radius:7px;padding:13px 22px;font-size:15px;font-weight:700;cursor:pointer;text-decoration:none;text-align:center}
    .primary{background:#0876b9;color:#fff;min-width:180px}.primary:hover{background:#05669f}.cancel{background:none;color:#687987;padding-inline:4px}.cancel:hover{color:#c52a38}
    .summary{padding:24px;align-self:start}.summary h2{margin:0 0 20px}.row{display:flex;justify-content:space-between;gap:20px;padding:11px 0;border-bottom:1px solid #edf1f4;font-size:14px}.row span{color:#718291}.row strong{text-align:right;overflow-wrap:anywhere}.amount{font-size:25px;color:#075ea9;margin-top:20px;text-align:right;font-weight:800}
    .otp{display:none}.otp.is-active{display:block}.checkout.is-hidden{display:none}.otp-code{font-size:22px;letter-spacing:8px;text-align:center;font-weight:700}.otp-note{padding:14px;background:#f1f8fc;border-left:3px solid #0876b9;margin:18px 0}
    .qr-box{display:none;text-align:center;padding:18px;border:1px dashed #a8bccb;border-radius:8px}.qr-box.is-active{display:block}.qr{width:150px;height:150px;margin:10px auto;background:repeating-conic-gradient(#162b3a 0 25%,#fff 0 50%) 0/18px 18px;border:10px solid #fff;outline:1px solid #d1dce4}
    @media(max-width:760px){header{padding:0 18px}.safe{display:none}main{grid-template-columns:1fr;margin:18px auto}.summary{order:-1}.content{padding:22px}.fields,.methods{grid-template-columns:1fr}.field--wide{grid-column:auto}.actions{align-items:stretch;flex-direction:column}.button{width:100%}}
  </style>
</head>
<body>
  <div class="topbar"></div>
  <header><div class="brand">VN<span>PAY</span></div><div class="safe">Thanh toán an toàn · Kết nối được mã hóa</div></header>
  <main>
    <section class="panel content">
      <div class="local"><strong>Môi trường mô phỏng local.</strong> Không nhập thông tin ngân hàng thật.</div>
      <div class="checkout" id="checkout">
        <h1>Chọn phương thức thanh toán</h1>
        <p>Hoàn tất thanh toán cho đơn hàng của bạn.</p>
        <div class="methods">
          <label class="method"><input type="radio" name="method" value="atm" checked><strong>Thẻ ATM nội địa</strong><small>Thẻ và tài khoản ngân hàng</small></label>
          <label class="method"><input type="radio" name="method" value="qr"><strong>VNPAY QR</strong><small>Quét mã bằng ứng dụng ngân hàng</small></label>
        </div>

        <div id="card-form">
          <h2>Thông tin thanh toán</h2>
          <div class="fields">
            <div class="field field--wide"><label for="bank">Ngân hàng</label><select id="bank"><option value="NCB">NCB</option><option value="VCB">Vietcombank</option><option value="BIDV">BIDV</option><option value="TCB">Techcombank</option></select></div>
            <div class="field field--wide"><label for="card-number">Số thẻ</label><input id="card-number" inputmode="numeric" value="9704198526191432198" maxlength="19"></div>
            <div class="field field--wide"><label for="card-name">Tên chủ thẻ</label><input id="card-name" value="NGUYEN VAN A"></div>
            <div class="field"><label for="issued-date">Ngày phát hành</label><input id="issued-date" value="07/15"></div>
          </div>
          <div class="hint">Dữ liệu demo: thẻ <code>9704198526191432198</code>. Đổi số cuối thành <code>9</code> để mô phỏng giao dịch thất bại.</div>
        </div>

        <div class="qr-box" id="qr-box"><strong>Quét mã để thanh toán</strong><div class="qr" aria-label="Mã QR mô phỏng"></div><p>Mở ứng dụng ngân hàng, quét mã và xác nhận giao dịch.</p></div>
        <div class="actions"><button class="button primary" id="continue" type="button">Tiếp tục</button><button class="button cancel" id="cancel" type="button">Hủy giao dịch</button></div>
      </div>

      <div class="otp" id="otp">
        <h1>Xác thực giao dịch</h1>
        <p>Mã OTP đã được gửi tới số điện thoại đăng ký **** 6789.</p>
        <div class="otp-note">Trong môi trường local, sử dụng mã OTP <strong>123456</strong>.</div>
        <div class="field"><label for="otp-code">Mã OTP</label><input class="otp-code" id="otp-code" inputmode="numeric" maxlength="6" placeholder="••••••"></div>
        <div class="actions"><button class="button primary" id="pay" type="button">Xác nhận thanh toán</button><button class="button cancel" id="back" type="button">Quay lại</button></div>
      </div>
    </section>

    <aside class="panel summary">
      <h2>Thông tin giao dịch</h2>
      <div class="row"><span>Mã giao dịch</span><strong>${escapeHtml(payment.vnp_TxnRef)}</strong></div>
      <div class="row"><span>Nội dung</span><strong>${escapeHtml(payment.vnp_OrderInfo)}</strong></div>
      <div class="row"><span>Đơn vị chấp nhận</span><strong>${escapeHtml(payment.vnp_TmnCode)}</strong></div>
      <div class="amount">${amount} ₫</div>
    </aside>
  </main>
  <script>
    const action = ${JSON.stringify(action)};
    const checkout = document.querySelector("#checkout");
    const otp = document.querySelector("#otp");
    const cardForm = document.querySelector("#card-form");
    const qrBox = document.querySelector("#qr-box");
    const selectedMethod = () => document.querySelector('input[name="method"]:checked').value;
    const finish = (result) => {
      const bank = document.querySelector("#bank").value;
      window.location.assign(action + "&mock_result=" + result + "&mock_bank=" + bank + "&mock_method=" + selectedMethod());
    };

    document.querySelectorAll('input[name="method"]').forEach((input) => input.addEventListener("change", () => {
      const qr = selectedMethod() === "qr";
      cardForm.style.display = qr ? "none" : "block";
      qrBox.classList.toggle("is-active", qr);
      document.querySelector("#continue").textContent = qr ? "Tôi đã thanh toán" : "Tiếp tục";
    }));
    document.querySelector("#continue").addEventListener("click", () => {
      if (selectedMethod() === "qr") return finish("success");
      if (!document.querySelector("#card-number").value.trim() || !document.querySelector("#card-name").value.trim()) return;
      checkout.classList.add("is-hidden");
      otp.classList.add("is-active");
      document.querySelector("#otp-code").focus();
    });
    document.querySelector("#pay").addEventListener("click", () => {
      const otpCode = document.querySelector("#otp-code").value;
      const cardNumber = document.querySelector("#card-number").value.replaceAll(" ", "");
      finish(otpCode === "123456" && !cardNumber.endsWith("9") ? "success" : "failed");
    });
    document.querySelector("#back").addEventListener("click", () => {
      otp.classList.remove("is-active");
      checkout.classList.remove("is-hidden");
    });
    document.querySelector("#cancel").addEventListener("click", () => finish("cancel"));
  </script>
</body></html>`;
}

function json(response, status, body) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  let body = "";
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 1_000_000) throw new Error("Request body is too large");
  }
  return JSON.parse(body || "{}");
}

function refundHashData(request) {
  return [
    "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
    "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount",
    "vnp_TransactionNo", "vnp_TransactionDate", "vnp_CreateBy",
    "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo",
  ].map((field) => request[field] ?? "").join("|");
}

function refundResponseHashData(response) {
  return [
    "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
    "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode",
    "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
    "vnp_TransactionStatus", "vnp_OrderInfo",
  ].map((field) => response[field] ?? "").join("|");
}

function refundResponse(request) {
  const response = {
    vnp_ResponseId: crypto.randomUUID().replaceAll("-", ""),
    vnp_Command: "refund",
    vnp_ResponseCode: "00",
    vnp_Message: "Refund success",
    vnp_TmnCode: tmnCode,
    vnp_TxnRef: request.vnp_TxnRef,
    vnp_Amount: request.vnp_Amount,
    vnp_BankCode: "NCB",
    vnp_PayDate: nowVnpay(),
    vnp_TransactionNo: transactionNo(request.vnp_TxnRef),
    vnp_TransactionType: "02",
    vnp_TransactionStatus: "00",
    vnp_OrderInfo: request.vnp_OrderInfo,
  };
  response.vnp_SecureHash = hmac(refundResponseHashData(response));
  return response;
}

async function handle(request, response) {
  const url = new URL(request.url, `http://${request.headers.host}`);

  if (request.method === "GET" && url.pathname === "/health") {
    return json(response, 200, { status: "UP" });
  }

  if (request.method === "GET" && url.pathname === "/paymentv2/vpcpay.html") {
    const payment = queryObject(url.searchParams);
    if (payment.vnp_TmnCode !== tmnCode
        || !validHash(canonicalize(payment), payment.vnp_SecureHash)) {
      return json(response, 400, { message: "Invalid payment signature" });
    }
    response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
    return response.end(paymentPage(payment, url.searchParams.toString()));
  }

  if (request.method === "GET" && url.pathname === "/complete") {
    const payment = queryObject(url.searchParams);
    const result = payment.mock_result;
    const bankCode = payment.mock_bank;
    const paymentMethod = payment.mock_method;
    delete payment.mock_result;
    delete payment.mock_bank;
    delete payment.mock_method;
    if (payment.vnp_TmnCode !== tmnCode
        || !validHash(canonicalize(payment), payment.vnp_SecureHash)) {
      return json(response, 400, { message: "Invalid payment signature" });
    }

    const callback = callbackParameters(
      payment,
      result,
      bankCode,
      paymentMethod,
    );
    const callbackQuery = signedQuery(callback);
    const ipnResponse = await fetch(`${merchantIpnUrl}?${callbackQuery}`);
    console.log("IPN", payment.vnp_TxnRef, await ipnResponse.text());

    const separator = payment.vnp_ReturnUrl.includes("?") ? "&" : "?";
    response.writeHead(302, {
      location: `${payment.vnp_ReturnUrl}${separator}${callbackQuery}`,
    });
    return response.end();
  }

  if (request.method === "POST"
      && url.pathname === "/merchant_webapi/api/transaction") {
    const refund = await readJson(request);
    if (refund.vnp_Command !== "refund"
        || refund.vnp_TmnCode !== tmnCode
        || !validHash(refundHashData(refund), refund.vnp_SecureHash)) {
      return json(response, 200, {
        vnp_ResponseCode: "97",
        vnp_Message: "Invalid checksum",
      });
    }
    if (!refunds.has(refund.vnp_RequestId)) {
      refunds.set(refund.vnp_RequestId, refundResponse(refund));
    }
    return json(response, 200, refunds.get(refund.vnp_RequestId));
  }

  return json(response, 404, { message: "Not found" });
}

http.createServer((request, response) => {
  handle(request, response).catch((error) => {
    console.error(error);
    if (!response.headersSent) json(response, 500, { message: error.message });
    else response.end();
  });
}).listen(port, "0.0.0.0", () => {
  console.log(`Mock VNPAY listening on ${port}`);
});
