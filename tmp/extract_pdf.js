const fs = require('fs');
const pdf = require('pdf-parse');
const buf = fs.readFileSync('C:\\Users\\Administrator\\Desktop\\1016原型调整说明 K.pdf');
pdf(buf).then(d => {
  fs.writeFileSync('tmp/pdf-1016.txt', d.text, 'utf8');
  console.log('pages:', d.numpages, 'chars:', d.text.length);
}).catch(e => { console.error('ERR', e.message); process.exit(1); });
