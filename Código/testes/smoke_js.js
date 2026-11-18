// Teste independente de interoperabilidade do Node.js com o servidor Java.
const assert = require('node:assert/strict');
const BASE = process.env.LOJA_API_URL || 'http://localhost:8080';

async function main() {
  const login = await fetch(`${BASE}/api/autenticacao`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: 'cliente', senha: '123' })
  });
  assert.equal(login.status, 200);
  const { token } = await login.json();
  const produtos = await fetch(`${BASE}/api/produtos`, { headers: { Authorization: `Bearer ${token}` } });
  assert.equal(produtos.status, 200);
  const dados = await produtos.json();
  assert.equal(dados.produtos.length, 4);
  assert.equal(dados.produtos[0].nome, 'Notebook');
  const inscricao = await fetch(`${BASE}/api/avisos/inscricoes`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }
  });
  assert.equal(inscricao.status, 201);
  const { inscricaoId } = await inscricao.json();
  const avisos = await fetch(`${BASE}/api/avisos/inscricoes/${inscricaoId}/eventos`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  assert.equal(avisos.status, 200);
  assert.deepEqual((await avisos.json()).avisos, []);
  const saida = await fetch(`${BASE}/api/avisos/inscricoes/${inscricaoId}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
  });
  assert.equal(saida.status, 200);
  console.log('OK: cliente Node.js fez login, listou produtos e consultou avisos.');
}

main().catch((erro) => { console.error(erro); process.exitCode = 1; });
