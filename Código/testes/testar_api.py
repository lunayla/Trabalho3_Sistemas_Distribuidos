"""Testes integrados da API: executa o servidor Java e verifica funcionalidades."""
import json
import os
import subprocess
import time
import unittest
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

RAIZ = Path(__file__).resolve().parents[1]
SERVIDOR = RAIZ / "servidor-java"
PORTA = int(os.environ.get("LOJA_TESTE_PORTA", "18091"))
URL = f"http://127.0.0.1:{PORTA}"


def pedir(metodo, caminho, dados=None, token=None):
    cabecalhos = {"Accept": "application/json"}
    if token: cabecalhos["Authorization"] = "Bearer " + token
    if dados is not None: cabecalhos["Content-Type"] = "application/json"
    corpo = json.dumps(dados).encode("utf-8") if dados is not None else None
    req = Request(URL + caminho, data=corpo, headers=cabecalhos, method=metodo)
    try:
        with urlopen(req, timeout=3) as resp:
            return resp.status, json.load(resp)
    except HTTPError as erro:
        return erro.code, json.load(erro)


class TesteLoja(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        (SERVIDOR / "build").mkdir(exist_ok=True)
        arquivos = [str(p) for p in (SERVIDOR / "src").glob("*.java")]
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(SERVIDOR / "build"), *arquivos], check=True)
        cls.processo = subprocess.Popen(["java", "-cp", str(SERVIDOR / "build"), "Servidor", str(PORTA)],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(60):
            if cls.processo.poll() is not None: raise RuntimeError("Servidor encerrou inesperadamente")
            try:
                pedir("GET", "/api/autenticacao")
                break
            except URLError:
                time.sleep(0.1)
        else:
            raise RuntimeError("Servidor nao iniciou")
        cls.cliente = pedir("POST", "/api/autenticacao", {"usuario": "cliente", "senha": "123"})[1]["token"]
        cls.admin = pedir("POST", "/api/autenticacao", {"usuario": "admin", "senha": "admin123"})[1]["token"]

    @classmethod
    def tearDownClass(cls):
        cls.processo.terminate()
        try: cls.processo.wait(timeout=5)
        except subprocess.TimeoutExpired: cls.processo.kill()

    def test_01_autenticacao_e_regras(self):
        self.assertEqual(pedir("POST", "/api/autenticacao", {"usuario": "cliente", "senha": "errada"})[0], 401)
        self.assertEqual(pedir("GET", "/api/produtos")[0], 401)
        self.assertEqual(pedir("GET", "/api/pedidos/total", token=self.cliente)[0], 403)
        self.assertEqual(pedir("POST", "/api/avisos", {"mensagem": "sem permissao"}, self.cliente)[0], 403)

    def test_02_produtos_e_heranca(self):
        codigo, dados = pedir("GET", "/api/produtos", token=self.cliente)
        self.assertEqual(codigo, 200)
        self.assertEqual(len(dados["produtos"]), 4)
        self.assertEqual({p["tipo"] for p in dados["produtos"]}, {"Fisico", "Digital"})
        codigo, dados = pedir("GET", "/api/produtos/2", token=self.cliente)
        self.assertEqual((codigo, dados["produto"]["nome"]), (200, "Mouse"))
        self.assertEqual(pedir("GET", "/api/produtos/999", token=self.cliente)[0], 404)

    def test_03_pedidos_notificacoes_multiplos_clientes(self):
        codigo, a = pedir("POST", "/api/avisos/inscricoes", token=self.cliente)
        self.assertEqual(codigo, 201)
        codigo, b = pedir("POST", "/api/avisos/inscricoes", token=self.admin)
        self.assertEqual(codigo, 201)
        sub_a, sub_b = a["inscricaoId"], b["inscricaoId"]
        try:
            self.assertEqual(pedir("GET", f"/api/avisos/inscricoes/{sub_b}/eventos", token=self.cliente)[0], 403)
            codigo, resposta = pedir("POST", "/api/pedidos", {"pedidoId": 101, "produtoId": 1, "quantidade": 2}, self.cliente)
            self.assertEqual(codigo, 201)
            self.assertIn("7000", resposta["mensagem"])
            self.assertEqual(pedir("POST", "/api/pedidos", {"pedidoId": 101, "produtoId": 1, "quantidade": 1}, self.cliente)[0], 400)
            self.assertEqual(pedir("POST", "/api/pedidos", {"pedidoId": 102, "produtoId": 1, "quantidade": 0}, self.cliente)[0], 400)
            self.assertEqual(pedir("POST", "/api/pedidos", {"pedidoId": 103, "produtoId": 999, "quantidade": 1}, self.cliente)[0], 404)
            self.assertEqual(pedir("GET", "/api/pedidos/total", token=self.admin)[1]["total"], 1)
            self.assertEqual(pedir("POST", "/api/avisos", {"mensagem": "Promocao de novembro!"}, self.admin)[0], 200)
            for token, sub in [(self.cliente, sub_a), (self.admin, sub_b)]:
                codigo, retorno = pedir("GET", f"/api/avisos/inscricoes/{sub}/eventos", token=token)
                self.assertEqual(codigo, 200)
                self.assertEqual(len(retorno["avisos"]), 2)
                self.assertIn("NOVO PEDIDO", retorno["avisos"][0]["mensagem"])
                self.assertIn("AVISO DA LOJA", retorno["avisos"][1]["mensagem"])
                self.assertEqual(pedir("GET", f"/api/avisos/inscricoes/{sub}/eventos", token=token)[1]["avisos"], [])
        finally:
            pedir("DELETE", f"/api/avisos/inscricoes/{sub_a}", token=self.cliente)
            pedir("DELETE", f"/api/avisos/inscricoes/{sub_b}", token=self.admin)

    def test_04_validacao_json(self):
        self.assertEqual(pedir("POST", "/api/pedidos", {"pedidoId": "abc", "produtoId": 1, "quantidade": 1}, self.cliente)[0], 400)
        self.assertEqual(pedir("POST", "/api/avisos", {"mensagem": "  "}, self.admin)[0], 400)


if __name__ == "__main__":
    unittest.main(verbosity=2)
