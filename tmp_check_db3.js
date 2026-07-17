const fs = require("fs");
const buf = fs.readFileSync("/workspace/backend/data/api-server.db");
const str = buf.toString("utf8");

// Look for all occurrences of "https://cltwchfz9p" (the custom provider URL)
let idx = 0;
while ((idx = str.indexOf("cltwchfz9p", idx)) !== -1) {
    const context = str.slice(Math.max(0, idx - 100), idx + 200).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
    console.log("Found at offset", idx, ":");
    console.log(context);
    console.log("---");
    idx += 1;
}

// Also look for "custom-openai"
let idx2 = 0;
while ((idx2 = str.indexOf("custom-openai", idx2)) !== -1) {
    const context = str.slice(Math.max(0, idx2 - 100), idx2 + 200).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
    console.log("Found custom-openai at offset", idx2, ":");
    console.log(context);
    console.log("---");
    idx2 += 1;
}
