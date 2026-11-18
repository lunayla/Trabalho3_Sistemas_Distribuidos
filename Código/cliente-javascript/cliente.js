/** Cliente da Loja Virtual API - JavaScript */
const readline = require('node:readline/promises');
const { stdin, stdout } = require('node:process');

const BASE_URL = (process.env.LOJA_API_URL || 'http://localhost:8080').replace(/\/$/, '');
const rl = readline.createInterface({ input: stdin, output: stdout });
let token = null;
let inscricaoId = null;
let intervalo = null;
let consultando = false;

async function requisitar(metodo, caminho, dados) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (dados !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
  let resposta;
  try {
    resposta = await fetch(`${BASE_URL}${caminho}`, {
      method: metodo,
      headers,
      body: dados === undefined ? undefined : JSON.stringify(dados),
      signal: AbortSignal.timeout(5000)
    });
  } catch {
    throw new Error(`Nao foi possivel conectar ao servidor (${BASE_URL}).`);
  }
  let json;
  try { json = await resposta.json(); }
  catch { throw new Error('Resposta invalida do servidor.'); }
  if (!resposta.ok) throw new Error(`HTTP ${resposta.status}: ${json.mensagem || 'Erro desconhecido'}`);
  return json;
}

async function consultarAvisos() {
  if (!inscricaoId || consultando) return;
  consultando = true;
  try {
    const resposta = await requisitar('GET', `/api/avisos/inscricoes/${inscricaoId}/eventos`);
    for (const aviso of resposta.avisos) {
      console.log(`\n[NOTIFICACAO] ${aviso.mensagem}\nOpcao: `);
    }
  } catch {
    // Mantem o menu caso a rede esteja temporariamente indisponivel.
  } finally {
    consultando = false;
  }
}

function exibirProduto(p) {
  console.log(`${p.id} - ${p.nome} - R$ ${p.preco.toFixed(2)} | ${p.categoria} | ${p.tipo}`);
}

async function numero(pergunta) {
  const texto = (await rl.question(pergunta)).trim();
  if (!/^\d+$/.test(texto)) throw new Error('Digite um numero inteiro valido.');
  return Number(texto);
}

async function encerrar() {
  if (intervalo) clearInterval(intervalo);
  if (inscricaoId) {
    try { await requisitar('DELETE', `/api/avisos/inscricoes/${inscricaoId}`); }
    catch { /* servidor pode ter sido desligado */ }
    inscricaoId = null;
  }
  rl.close();
}

async function main() {
  console.log('=== LOJA VIRTUAL | CLIENTE JAVASCRIPT ===');
  try {
    const usuario = (await rl.question('Usuario (cliente/admin): ')).trim();
    const senha = await rl.question('Senha: ');
    const login = await requisitar('POST', '/api/autenticacao', { usuario, senha });
    token = login.token;
    inscricaoId = (await requisitar('POST', '/api/avisos/inscricoes')).inscricaoId;
    intervalo = setInterval(consultarAvisos, 2000);
    console.log('Conectado a loja! Avisos ativados.');
    while (true) {
      console.log('\n1 - Listar produtos');
      if (usuario === 'admin') {
        console.log('2 - Consultar total de pedidos');
        console.log('3 - Enviar aviso a todos os clientes');
      } else {
        console.log('2 - Fazer pedido');
        console.log('3 - Consultar produto');
      }
      console.log('0 - Sair');
      const opcao = (await rl.question('Opcao: ')).trim();
      try {
        if (opcao === '0') break;
        if (opcao === '1') {
          const resposta = await requisitar('GET', '/api/produtos');
          resposta.produtos.forEach(exibirProduto);
        } else if (opcao === '2' && usuario === 'admin') {
          console.log((await requisitar('GET', '/api/pedidos/total')).mensagem);    
        } else if (opcao === '2') {
          const produtoId = await numero('ID do produto: ');
          const quantidade = await numero('Quantidade: ');
          console.log((await requisitar('POST', '/api/pedidos', { produtoId, quantidade })).mensagem);        } else if (opcao === '3' && usuario === 'admin') {
          const mensagem = await rl.question('Texto do aviso: ');
          console.log((await requisitar('POST', '/api/avisos', { mensagem })).mensagem);
        } else if (opcao === '3') {
          const produtoId = await numero('ID do produto: ');
          exibirProduto((await requisitar('GET', `/api/produtos/${produtoId}`)).produto);
        } else {
          console.log('Opcao invalida.');
        }
      } catch (erro) {
        console.log('Erro:', erro.message);
      }
    }
  } catch (erro) {
    if (erro.code !== 'ERR_USE_AFTER_CLOSE' && erro.code !== 'ERR_STREAM_PREMATURE_CLOSE') {
      console.log('Erro:', erro.message);
    }
  } finally {
    await encerrar();
  }
}

main();
