const fs = require("fs");
const buf = fs.readFileSync("/workspace/backend/data/api-server.db");
const str = buf.toString("utf8");

// Look for all occurrences of "sk-TWvf" (API key prefix)
let idx = 0;
while ((idx = str.indexOf("sk-TWvf", idx)) !== -1) {
    const context = str.slice(Math.max(0, idx - 50), idx + 150).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
    console.log("Found sk-TWvf at offset", idx, ":");
    console.log(context);
    console.log("---");
    idx += 1;
}

// Look for t_ai_subscription table data
console.log("\n\nSearching for subscription data...");
let subIdx = 0;
while ((subIdx = str.indexOf("qwen3.6-35b-a3b", subIdx)) !== -1) {
    const context = str.slice(Math.max(0, subIdx - 150), subIdx + 150).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
    console.log("Found qwen3.6-35b-a3b at offset", subIdx, ":");
    console.log(context);
    console.log("---");
    subIdx += 1;
}
