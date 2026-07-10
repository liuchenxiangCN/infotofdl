import com.fr.plugin.tptj.ivan.jsd19083.util.xml.WriterTargetSchemaPolicy;

public final class WriterTargetSchemaPolicyProbe {
    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    public static void main(String[] args) {
        assertEquals("explicit target owner wins", "TARGET_OWNER",
            WriterTargetSchemaPolicy.select("TARGET_OWNER", "PRIP3KF", "ANONYMOUS"));
        assertEquals("Oracle writer user wins over unreliable default schema", "PRIP3KF",
            WriterTargetSchemaPolicy.select("", "PRIP3KF", "ANONYMOUS"));
        assertEquals("non-Oracle writer default schema is used without an Oracle user", "WRITER_DEFAULT",
            WriterTargetSchemaPolicy.select("", "", "WRITER_DEFAULT"));
        assertEquals("FDL empty-schema sentinel remains empty", "",
            WriterTargetSchemaPolicy.select("", "", "__EMPTY__"));
        assertEquals("blank inputs remain blank", "",
            WriterTargetSchemaPolicy.select(" ", null, ""));
        assertEquals("source owner is never a policy input", "PRIP3KF",
            WriterTargetSchemaPolicy.select("", "PRIP3KF", "ANONYMOUS"));
        System.out.println("PASS writer target schema policy");
    }
}
