import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Objeto distribuido 3: avisos e inscricoes via HTTP (polling, sem RMI/callback). */
public final class AvisoApi implements HttpHandler {
    private final AvisoService avisos;
    private final AutenticacaoService autenticacao;

    public AvisoApi(AvisoService avisos, AutenticacaoService autenticacao) {
        this.avisos = avisos;
        this.autenticacao = autenticacao;
    }

    @Override public void handle(HttpExchange troca) throws IOException {
        HttpUtil.tratar(troca, () -> {
            String caminho = troca.getRequestURI().getPath();
            if ("/api/avisos".equals(caminho)) {
                HttpUtil.exigirMetodo(troca, "POST");
                autenticacao.exigirAdmin(troca);
                Map<String, String> dados = HttpUtil.jsonDaRequisicao(troca);
                String mensagem = HttpUtil.texto(dados, "mensagem");
                avisos.enviar("AVISO DA LOJA: " + mensagem);
                HttpUtil.enviar(troca, 200, JsonSimples.criar("status", "OK", "mensagem", "Aviso enviado aos clientes."));
            } else if ("/api/avisos/inscricoes".equals(caminho)) {
                HttpUtil.exigirMetodo(troca, "POST");
                String usuario = autenticacao.usuario(troca);
                String id = avisos.entrar(usuario);
                HttpUtil.enviar(troca, 201, JsonSimples.criar("status", "OK", "inscricaoId", id));
            } else if (caminho.matches("/api/avisos/inscricoes/[^/]+/eventos")) {
                HttpUtil.exigirMetodo(troca, "GET");
                String usuario = autenticacao.usuario(troca);
                String id = caminho.substring("/api/avisos/inscricoes/".length(), caminho.length() - "/eventos".length());
                List<String> retorno = new ArrayList<>();
                for (String aviso : avisos.receber(id, usuario)) retorno.add(JsonSimples.criar("mensagem", aviso));
                HttpUtil.enviar(troca, 200, "{\"status\":\"OK\",\"avisos\":[" + String.join(",", retorno) + "]}");
            } else if (caminho.matches("/api/avisos/inscricoes/[^/]+")) {
                HttpUtil.exigirMetodo(troca, "DELETE");
                String usuario = autenticacao.usuario(troca);
                String id = caminho.substring("/api/avisos/inscricoes/".length());
                avisos.sair(id, usuario);
                HttpUtil.enviar(troca, 200, JsonSimples.criar("status", "OK", "mensagem", "Inscricao removida."));
            } else throw new ErroApi(404, "Recurso nao encontrado.");
        });
    }
}
