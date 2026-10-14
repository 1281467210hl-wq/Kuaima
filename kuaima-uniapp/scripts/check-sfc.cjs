// 自测脚本：用 @vue/compiler-sfc 对指定 .vue 做 parse + compileTemplate + compileScript，零错误即通过
// 用法: node scripts/check-sfc.cjs src/pages/boss/resume.vue ...
const fs = require("fs");
const path = require("path");
const { parse, compileTemplate, compileScript } = require("@vue/compiler-sfc");

const root = path.resolve(__dirname, "..");
const files = process.argv.slice(2);
if (!files.length) {
  console.error("usage: node scripts/check-sfc.cjs <relative vue paths>");
  process.exit(2);
}

let failed = 0;
for (const rel of files) {
  const file = path.resolve(root, rel);
  const source = fs.readFileSync(file, "utf8");
  const { descriptor, errors } = parse(source, { filename: file });
  const problems = [...errors];

  if (descriptor.template) {
    const compiled = compileTemplate({
      source: descriptor.template.content,
      filename: file,
      id: rel,
    });
    problems.push(...compiled.errors);
  }
  if (descriptor.script || descriptor.scriptSetup) {
    try {
      compileScript(descriptor, { id: rel });
    } catch (e) {
      problems.push(e.message);
    }
  }
  if (problems.length) {
    failed += 1;
    console.log(`[FAIL] ${rel}`);
    problems.forEach((p) => console.log("   - " + (p.message || p)));
  } else {
    console.log(`[ OK ] ${rel}`);
  }
}
process.exit(failed ? 1 : 0);
