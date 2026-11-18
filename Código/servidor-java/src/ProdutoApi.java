import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Objeto distribuido 1: catalogo de produtos exposto por HTTP/JSON. */
public final class ProdutoApi implements HttpHandler {
    private final ProdutoService produtos;
    private final AutenticacaoService autenticacao;

    public ProdutoApi(ProdutoService produtos, AutenticacaoService autenticacao) {
        this.produtos = produtos;
        this.autenticacao = autenticacao;
    }

    @Override public void handle(HttpExchange troca) throws IOException {
        HttpUtil.tratar(troca, () -> {
            HttpUtil.exigirMetodo(troca, "GET");
            autenticacao.usuario(troca);
            String caminho = troca.getRequestURI().getPath();
            if ("/api/produtos".equals(caminho)) {
                List<String> dados = produtos.listar().stream().map(ProdutoApi::jsonProduto).collect(Collectors.toList());
                HttpUtil.enviar(troca, 200, "{\"status\":\"OK\",\"produtos\":[" + String.join(",", dados) + "]}");
            } else if (caminho.matches("/api/produtos/[^/]+")) {
                int id = HttpUtil.inteiro(caminho.substring("/api/produtos/".length()), "produtoId");
                Produto produto = produtos.buscar(id);
                if (produto == null) throw new ErroApi(404, "Produto nao encontrado.");
                HttpUtil.enviar(troca, 200, "{\"status\":\"OK\",\"produto\":" + jsonProduto(produto) + "}");
            } else throw new ErroApi(404, "Recurso nao encontrado.");
        });
    }

    private static String jsonProduto(Produto p) {
        return JsonSimples.criar("id", p.getId(), "nome", p.getNome(), "preco", p.getPreco(),
                "categoria", p.getCategoria().getNome(), "tipo", p.getTipo());
    }
}
