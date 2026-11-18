import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Utilitarios HTTP. O servidor usa a API HTTP nativa do JDK, nunca Socket/RMI. */
public final class HttpUtil {
    private HttpUtil() { }

    @FunctionalInterface
    public interface Acao { void executar() throws IOException; }

    public static void tratar(HttpExchange troca, Acao acao) throws IOException {
        try {
            troca.getResponseHeaders().set("Cache-Control", "no-store");
            acao.executar();
        } catch (ErroApi e) {
            enviar(troca, e.getCodigo(), JsonSimples.criar("status", "ERRO", "mensagem", e.getMessage()));
        } catch (IllegalArgumentException e) {
            enviar(troca, 400, JsonSimples.criar("status", "ERRO", "mensagem", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Erro inesperado: " + e);
            enviar(troca, 500, JsonSimples.criar("status", "ERRO", "mensagem", "Erro interno do servidor."));
        } finally {
            troca.close();
        }
    }

    public static void enviar(HttpExchange troca, int codigo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(codigo, bytes.length);
        try (var saida = troca.getResponseBody()) { saida.write(bytes); }
    }

    public static Map<String, String> jsonDaRequisicao(HttpExchange troca) throws IOException {
        byte[] bytes = troca.getRequestBody().readNBytes(65_537);
        if (bytes.length > 65_536) throw new ErroApi(413, "Corpo da requisicao muito grande.");
        try {
            return JsonSimples.ler(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new ErroApi(400, "JSON invalido: " + e.getMessage());
        }
    }

    public static void exigirMetodo(HttpExchange troca, String metodo) {
        if (!metodo.equals(troca.getRequestMethod()))
            throw new ErroApi(405, "Metodo HTTP nao permitido. Use " + metodo + ".");
    }

    public static String texto(Map<String, String> dados, String chave) {
        String texto = dados.get(chave);
        if (texto == null || texto.isBlank()) throw new ErroApi(400, "Campo obrigatorio: " + chave);
        return texto.trim();
    }

    public static int inteiro(Map<String, String> dados, String chave) {
        try { return Integer.parseInt(texto(dados, chave)); }
        catch (NumberFormatException e) { throw new ErroApi(400, "Numero invalido: " + chave); }
    }

    public static int inteiro(String numero, String descricao) {
        try { return Integer.parseInt(numero); }
        catch (NumberFormatException e) { throw new ErroApi(400, "Numero invalido: " + descricao); }
    }
}
