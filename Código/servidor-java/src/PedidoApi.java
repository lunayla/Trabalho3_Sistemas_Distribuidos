import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/** Objeto distribuido 2: registro e consulta de pedidos por HTTP/JSON. */
public final class PedidoApi implements HttpHandler {
    private final PedidoService pedidos;
    private final ProdutoService produtos;
    private final AutenticacaoService autenticacao;
    private final AvisoService avisos;

    public PedidoApi(PedidoService pedidos, ProdutoService produtos, AutenticacaoService autenticacao,
                     AvisoService avisos) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.autenticacao = autenticacao;
        this.avisos = avisos;
    }

    @Override public void handle(HttpExchange troca) throws IOException {
        HttpUtil.tratar(troca, () -> {
            String caminho = troca.getRequestURI().getPath();
            if ("/api/pedidos".equals(caminho)) {
                HttpUtil.exigirMetodo(troca, "POST");
                String usuario = autenticacao.usuario(troca);
                Map<String, String> dados = HttpUtil.jsonDaRequisicao(troca);
                
                int produtoId = HttpUtil.inteiro(dados, "produtoId");
                int quantidade = HttpUtil.inteiro(dados, "quantidade");

                Produto produto = produtos.buscar(produtoId);

                if (produto == null)
                    throw new ErroApi(404, "Produto nao encontrado.");

                Pedido pedido = pedidos.registrar(usuario, produto, quantidade);
                String mensagem = PedidoService.mensagemRegistro(pedido, produto);

                avisos.enviar("NOVO PEDIDO: " + pedido.getId() + " registrado.");
                HttpUtil.enviar(troca, 201, JsonSimples.criar("status", "OK", "mensagem", mensagem,
                        "pedidoId", pedido.getId()));
                        
            } else if ("/api/pedidos/total".equals(caminho)) {
                HttpUtil.exigirMetodo(troca, "GET");
                autenticacao.exigirAdmin(troca);
                int total = pedidos.consultarTotal();
                HttpUtil.enviar(troca, 200, JsonSimples.criar("status", "OK", "mensagem",
                        "Total de pedidos recebidos: " + total, "total", total));
            } else throw new ErroApi(404, "Recurso nao encontrado.");
        });
    }
}
