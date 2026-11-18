import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sessoes simples em memoria: demonstracao, sem banco de dados. */
public class AutenticacaoService {
    private final Map<String, String> sessoes = new ConcurrentHashMap<>();

    public String autenticar(String usuario, String senha) {
        boolean valido = ("cliente".equals(usuario) && "123".equals(senha))
                || ("admin".equals(usuario) && "admin123".equals(senha));
        if (!valido) throw new ErroApi(401, "Login invalido.");
        String token = UUID.randomUUID().toString();
        sessoes.put(token, usuario);
        return token;
    }

    public String usuario(HttpExchange troca) {
        String cabecalho = troca.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer "))
            throw new ErroApi(401, "Autenticacao obrigatoria.");
        String usuario = sessoes.get(cabecalho.substring(7));
        if (usuario == null) throw new ErroApi(401, "Sessao invalida.");
        return usuario;
    }

    public void exigirAdmin(HttpExchange troca) {
        if (!"admin".equals(usuario(troca))) throw new ErroApi(403, "Acesso exclusivo do administrador.");
    }
}
