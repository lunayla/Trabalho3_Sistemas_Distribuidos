import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Inicia a API HTTP da loja; nao implementa sockets ou RMI manualmente. */
public final class Servidor {
    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        ProdutoService produtos = new ProdutoService();
        PedidoService pedidos = new PedidoService();
        AvisoService avisos = new AvisoService();
        AutenticacaoService autenticacao = new AutenticacaoService();

        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/autenticacao", new AutenticacaoApi(autenticacao));
        servidor.createContext("/api/produtos", new ProdutoApi(produtos, autenticacao));
        servidor.createContext("/api/pedidos", new PedidoApi(pedidos, produtos, autenticacao, avisos));
        servidor.createContext("/api/avisos", new AvisoApi(avisos, autenticacao));
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
        System.out.println("Loja Virtual API pronta em http://localhost:" + porta);
        System.out.println("\nFuncionalidades disponiveis:");
        System.out.println("  - Catalogo de produtos");
        System.out.println("  - Gerenciamento de pedidos");
        System.out.println("  - Central de notificacoes");
        System.out.println("  - Autenticacao de usuarios");
        System.out.println("\nPressione Ctrl+C para encerrar o servidor.");
    }
}
