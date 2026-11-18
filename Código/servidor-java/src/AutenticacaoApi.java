import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/** Objeto de API de autenticacao. */
public final class AutenticacaoApi implements HttpHandler {
    private final AutenticacaoService autenticacao;
    public AutenticacaoApi(AutenticacaoService autenticacao) { this.autenticacao = autenticacao; }

    @Override public void handle(HttpExchange troca) throws IOException {
        HttpUtil.tratar(troca, () -> {
            if (!"/api/autenticacao".equals(troca.getRequestURI().getPath()))
                throw new ErroApi(404, "Recurso nao encontrado.");
            HttpUtil.exigirMetodo(troca, "POST");
            Map<String, String> dados = HttpUtil.jsonDaRequisicao(troca);
            String usuario = HttpUtil.texto(dados, "usuario");
            String token = autenticacao.autenticar(usuario, HttpUtil.texto(dados, "senha"));
            HttpUtil.enviar(troca, 200, JsonSimples.criar("status", "OK", "mensagem", "Login autorizado.",
                    "usuario", usuario, "token", token));
        });
    }
}
