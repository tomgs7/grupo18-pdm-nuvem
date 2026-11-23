package pt.ubi.pdm.grupo17.nuvem;

/** Configuração da demonstração. */
public final class Config {

    /**
     * true  -> fotografias no Cloud Storage for Firebase (exige o plano Blaze desde 03/02/2026;
     *          o uso gratuito continua incluído, mas é preciso associar uma conta de faturação).
     * false -> miniatura comprimida guardada no próprio Firestore (funciona no plano gratuito Spark).
     */
    public static final boolean USE_CLOUD_STORAGE = false;

    /** Tamanho máximo de uma fotografia (igual ao limite das Storage Rules). */
    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private Config() { }
}
