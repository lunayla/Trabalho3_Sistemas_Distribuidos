import java.util.HashMap;
import java.util.Map;

/** Registra pedidos no servidor automaticamente, evitando IDs repetidos. */
public class PedidoService {
    private final Map<String, ClienteLoja> clientes = new HashMap<>();
    private int proximoId = 1;
    private int total = 0;

    public synchronized Pedido registrar(String usuario, Produto produto, int quantidade) {
        if (quantidade <= 0)
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");

        if (produto == null)
            throw new IllegalArgumentException("Produto nao encontrado.");

        Pedido pedido = new Pedido(proximoId, usuario, produto.getId(), quantidade);

        ClienteLoja cliente = clientes.computeIfAbsent(usuario, ClienteLoja::new);
        cliente.adicionarPedido(pedido);

        proximoId++;
        total++;

        return pedido;
    }

    public static String mensagemRegistro(Pedido pedido, Produto produto) {
        double valor = pedido.getQuantidade() * produto.getPreco();

        return "Pedido " + pedido.getId() + " registrado para " + pedido.getCliente()
                + ". Total: R$ " + String.format("%.2f", valor);
    }

    public synchronized int consultarTotal() {
        return total;
    }
}
