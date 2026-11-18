"""Cliente da Loja Virtual API - Python."""
import json
import os
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

URL_BASE = os.environ.get("LOJA_API_URL", "http://localhost:8080").rstrip("/")


class ErroCliente(Exception):
    pass


class LojaApi:
    def __init__(self):
        self.token = None

    def requisitar(self, metodo, caminho, dados=None):
        cabecalhos = {"Accept": "application/json"}
        if self.token:
            cabecalhos["Authorization"] = "Bearer " + self.token
        conteudo = None
        if dados is not None:
            conteudo = json.dumps(dados, ensure_ascii=False).encode("utf-8")
            cabecalhos["Content-Type"] = "application/json; charset=utf-8"
        req = Request(URL_BASE + caminho, data=conteudo, method=metodo, headers=cabecalhos)
        try:
            with urlopen(req, timeout=5) as resposta:
                return json.load(resposta)
        except HTTPError as erro:
            try:
                mensagem = json.load(erro).get("mensagem", str(erro))
            except (ValueError, UnicodeError):
                mensagem = str(erro)
            raise ErroCliente(f"HTTP {erro.code}: {mensagem}") from erro
        except (URLError, TimeoutError, OSError) as erro:
            raise ErroCliente(f"Nao foi possivel conectar ao servidor ({URL_BASE}).") from erro

    def autenticar(self, usuario, senha):
        resposta = self.requisitar("POST", "/api/autenticacao", {"usuario": usuario, "senha": senha})
        self.token = resposta["token"]


class AvisosAutomaticos:
    """Consulta a API periodicamente, sem criar servidor ou callback no cliente."""
    def __init__(self, api):
        self.api = api
        self.inscricao_id = None
        self.parar = threading.Event()
        self.thread = None

    def iniciar(self):
        self.inscricao_id = self.api.requisitar("POST", "/api/avisos/inscricoes")["inscricaoId"]
        self.thread = threading.Thread(target=self._consultar, daemon=True)
        self.thread.start()

    def _consultar(self):
        while not self.parar.wait(2):
            try:
                resposta = self.api.requisitar("GET", f"/api/avisos/inscricoes/{self.inscricao_id}/eventos")
                for aviso in resposta["avisos"]:
                    print("\n[NOTIFICACAO]", aviso["mensagem"], "\nOpcao: ", end="", flush=True)
            except ErroCliente:
                # Uma falha temporaria de rede nao encerra o menu.
                continue

    def encerrar(self):
        self.parar.set()
        if self.thread is not None:
            self.thread.join(timeout=3)
        if self.inscricao_id:
            try:
                self.api.requisitar("DELETE", f"/api/avisos/inscricoes/{self.inscricao_id}")
            except ErroCliente:
                pass


def solicitar_inteiro(texto):
    return int(input(texto).strip())


def main():
    api = LojaApi()
    avisos = AvisosAutomaticos(api)
    print("=== LOJA VIRTUAL | CLIENTE PYTHON ===")
    try:
        usuario = input("Usuario (cliente/admin): ").strip()
        senha = input("Senha: ")
        api.autenticar(usuario, senha)
        avisos.iniciar()
        print("Conectado a loja! Avisos ativados.")
        while True:
            print("\n1 - Listar produtos")
            if usuario == "admin":
                print("2 - Consultar total de pedidos")
                print("3 - Enviar aviso a todos os clientes")
            else:
                print("2 - Fazer pedido")
                print("3 - Consultar produto")
            print("0 - Sair")
            opcao = input("Opcao: ").strip()
            try:
                if opcao == "0":
                    break
                if opcao == "1":
                    produtos = api.requisitar("GET", "/api/produtos")["produtos"]
                    for p in produtos:
                        print(f'{p["id"]} - {p["nome"]} - R$ {p["preco"]:.2f} | {p["categoria"]} | {p["tipo"]}')
                elif opcao == "2" and usuario == "admin":
                    print(api.requisitar("GET", "/api/pedidos/total")["mensagem"])
                elif opcao == "2":
                    pedido = {
                        "produtoId": solicitar_inteiro("ID do produto: "),
                        "quantidade": solicitar_inteiro("Quantidade: "),
                    }
                    print(api.requisitar("POST", "/api/pedidos", pedido)["mensagem"])
                elif opcao == "3" and usuario == "admin":
                    print(api.requisitar("POST", "/api/avisos", {"mensagem": input("Texto do aviso: ")})["mensagem"])
                elif opcao == "3":
                    produto_id = solicitar_inteiro("ID do produto: ")
                    p = api.requisitar("GET", f"/api/produtos/{produto_id}")["produto"]
                    print(f'{p["id"]} - {p["nome"]} - R$ {p["preco"]:.2f} | {p["categoria"]} | {p["tipo"]}')
                else:
                    print("Opcao invalida.")
            except ValueError:
                print("Digite um numero inteiro valido.")
            except ErroCliente as erro:
                print("Erro:", erro)
    except ErroCliente as erro:
        print("Erro:", erro)
    except (KeyboardInterrupt, EOFError):
        print("\nEncerrando...")
    finally:
        avisos.encerrar()


if __name__ == "__main__":
    main()
