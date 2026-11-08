const http = require("http");
const fs = require("fs");
const path = require("path");
const mime = { ".html": "text/html", ".js": "text/javascript", ".json": "application/json", ".png": "image/png", ".jpg": "image/jpeg", ".apk": "application/vnd.android.package-archive", ".css": "text/css" };
http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split("?")[0]);
  if (p === "/") p = "/index.html";
  const f = path.join(process.cwd(), p);
  if (!f.startsWith(process.cwd())) { res.writeHead(403); return res.end(); }
  fs.readFile(f, (err, data) => {
    if (err) { res.writeHead(404); return res.end("Not found"); }
    res.writeHead(200, { "Content-Type": mime[path.extname(f)] || "application/octet-stream" });
    res.end(data);
  });
}).listen(3000, "0.0.0.0", () => console.log("listening on 3000"));
