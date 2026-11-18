/** Erro esperado que sera retornado em HTTP com uma mensagem JSON. */
public final class ErroApi extends RuntimeException {
    private final int codigo;

    public ErroApi(int codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public int getCodigo() { return codigo; }
}
