import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inscricoes independentes: cada cliente recebe sua propria fila de avisos. */
public final class AvisoService {
    public static final class Inscricao {
        private final String usuario;
        private final List<String> fila = new ArrayList<>();
        private Inscricao(String usuario) { this.usuario = usuario; }
    }

    private final Map<String, Inscricao> inscricoes = new HashMap<>();

    public synchronized String entrar(String usuario) {
        String id = UUID.randomUUID().toString();
        inscricoes.put(id, new Inscricao(usuario));
        return id;
    }

    public synchronized void sair(String id, String usuario) {
        validar(id, usuario);
        inscricoes.remove(id);
    }

    public synchronized List<String> receber(String id, String usuario) {
        Inscricao inscricao = validar(id, usuario);
        List<String> mensagens = List.copyOf(inscricao.fila);
        inscricao.fila.clear();
        return mensagens;
    }

    public synchronized void enviar(String mensagem) {
        for (Inscricao inscricao : inscricoes.values()) inscricao.fila.add(mensagem);
        System.out.println("[AVISO] " + mensagem + " | inscritos: " + inscricoes.size());
    }

    public synchronized int quantidadeInscritos() { return inscricoes.size(); }

    private Inscricao validar(String id, String usuario) {
        Inscricao inscricao = inscricoes.get(id);
        if (inscricao == null) throw new ErroApi(404, "Inscricao nao encontrada.");
        if (!inscricao.usuario.equals(usuario)) throw new ErroApi(403, "Inscricao pertence a outro usuario.");
        return inscricao;
    }
}
