import com.fr.dp.datasource.dto.DBFieldTransferBean;
import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.dp.datasource.general.base.SqlFieldBean;
import com.fr.dp.util.JsonUtil;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.FieldTransferFactory;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.InfaFieldMeta;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.MappingFieldBinding;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.TargetWriteInfo;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.DataSyncParser;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.InfaSqlProjectionNormalizer;
import java.io.File;
import java.lang.reflect.Method;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class FieldMappingRegressionProbe {
    public static void main(String[] args) throws Exception {
        assertTypeMappings();
        assertTypedFactoryIgnoresRuntimeConflict();
        assertRuntimeEnrichesOnlyMissingXmlMetadata();
        assertSqlProjectionNormalization();
        if (args.length > 0) {
            assertRealXmlBindings(new File(args[0]));
        }
        if (args.length > 1) {
            assertApiClassification(new File(args[0]), new File(args[1]));
        }
        System.out.println("PASS typed field mapping probe: XML types, connector identity, SQL aliases, API classification");
    }

    private static void assertTypeMappings() {
        assertType(meta("read_num", InfaFieldMeta.KIND_TRANSFORM, "decimal", 16, 2), Types.DECIMAL, "DECIMAL");
        assertType(meta("write_num", InfaFieldMeta.KIND_TARGET, "number(p,s)", 16, 2), Types.NUMERIC, "NUMBER");
        assertType(meta("write_date", InfaFieldMeta.KIND_TARGET, "date", 19, 0), Types.TIMESTAMP, "DATE");
        assertType(meta("read_string", InfaFieldMeta.KIND_TRANSFORM, "string", 20, 0), Types.VARCHAR, "VARCHAR");
        SqlFieldBean unsupported = meta("bad", InfaFieldMeta.KIND_TRANSFORM, "opaque-new-type", 0, 0).toSqlFieldBean("bad");
        require(unsupported.getType() == Types.OTHER && unsupported.getInvalidWarning() != null,
            "unknown Informatica type must be explicit OTHER, not type=0/string");
    }

    private static void assertTypedFactoryIgnoresRuntimeConflict() {
        FieldLineageInfo lineage = new FieldLineageInfo();
        MappingFieldBinding binding = binding("target_a", "AMOUNT", "AMOUNT_EXPR",
            meta("AMOUNT_EXPR", InfaFieldMeta.KIND_TRANSFORM, "decimal", 18, 4),
            meta("AMOUNT", InfaFieldMeta.KIND_TARGET, "number(p,s)", 18, 4));
        lineage.addFieldBinding(binding);
        TargetWriteInfo target = new TargetWriteInfo();
        target.setTargetInstance("target_a");

        DBFieldTransferBean runtime = new DBFieldTransferBean();
        SqlFieldBean runtimeRead = new SqlFieldBean();
        runtimeRead.setName("AMOUNT_EXPR");
        runtimeRead.setType(Types.CHAR);
        runtimeRead.setTypeName("CHAR");
        SqlFieldBean runtimeWrite = new SqlFieldBean();
        runtimeWrite.setName("AMOUNT");
        runtimeWrite.setType(Types.CHAR);
        runtimeWrite.setTypeName("CHAR");
        runtime.setOrigin(Arrays.asList(runtimeRead));
        runtime.setTarget(Arrays.asList(runtimeWrite));

        List<FieldTransferBean> items = FieldTransferFactory.createForSync(runtime, lineage, target);
        require(items.size() == 1, "connector-proven field binding missing");
        require(items.get(0).getReadColumn().getType() == Types.DECIMAL, "runtime preview replaced XML read type");
        require(items.get(0).getWriteColumn().getType() == Types.NUMERIC, "runtime preview replaced XML target type");
        String json = JsonUtil.toJsonString(items);
        require(json.contains("AMOUNT_EXPR") && json.contains("AMOUNT") && !json.contains("\"type\":0"),
            "serialized field transfer lost names or types: " + json);
    }

    private static void assertRuntimeEnrichesOnlyMissingXmlMetadata() {
        FieldLineageInfo lineage = new FieldLineageInfo();
        MappingFieldBinding binding = binding("target_b", "NAME", "NAME", null,
            meta("NAME", InfaFieldMeta.KIND_TARGET, "varchar2", 50, 0));
        binding.setStatus(MappingFieldBinding.RUNTIME_REQUIRED);
        lineage.addFieldBinding(binding);
        TargetWriteInfo target = new TargetWriteInfo();
        target.setTargetInstance("target_b");
        SqlFieldBean runtimeRead = new SqlFieldBean();
        runtimeRead.setName("NAME");
        runtimeRead.setType(Types.VARCHAR);
        runtimeRead.setTypeName("VARCHAR");
        DBFieldTransferBean runtime = new DBFieldTransferBean();
        runtime.setOrigin(Arrays.asList(runtimeRead));
        runtime.setTarget(new ArrayList<SqlFieldBean>());
        List<FieldTransferBean> items = FieldTransferFactory.createForSync(runtime, lineage, target);
        require(items.size() == 1 && items.get(0).getReadColumn().getType() == Types.VARCHAR
            && items.get(0).getWriteColumn().getType() == Types.VARCHAR,
            "runtime metadata did not enrich a connector-proven missing XML type");
    }

    private static void assertSqlProjectionNormalization() {
        FieldLineageInfo literal = projection("A", "TAXRATE", "T2_CODE");
        String original = "select a, 0.0000, substr(code,1,4) 中文别名 from t";
        String normalized = InfaSqlProjectionNormalizer.normalize(original, literal);
        require(normalized.contains("a AS A") && normalized.contains("0.0000 AS TAXRATE")
            && normalized.contains("substr(code,1,4) AS T2_CODE"), "literal/expression aliases not normalized: " + normalized);
        require(original.equals(literal.getOriginalSql()) && normalized.equals(literal.getEffectiveSql()),
            "original/effective SQL provenance was not retained");

        FieldLineageInfo duplicate = projection("COMCODE", "MANAGECOM");
        String union = InfaSqlProjectionNormalizer.normalize(
            "select t.managecom 机构编码, t.managecom 机构编码 from t union all select x.managecom 机构编码, x.managecom 机构编码 from x",
            duplicate);
        require(count(union, " AS COMCODE") == 2 && count(union, " AS MANAGECOM") == 2,
            "duplicate labels were not replaced in every UNION branch: " + union);

        FieldLineageInfo prefix = projection("A", "B", "C");
        String prefixSql = InfaSqlProjectionNormalizer.normalize("select a, b from t", prefix);
        require(InfaSqlProjectionNormalizer.PREFIX_PROVEN.equals(prefix.getSqlProjectionStatus())
            && !prefix.isReaderFieldAvailable("C") && prefixSql.contains("b AS B"),
            "projection suffix must remain unresolved");

        FieldLineageInfo star = projection("A");
        String starSql = InfaSqlProjectionNormalizer.normalize("select * from t", star);
        require("select * from t".equals(starSql)
            && InfaSqlProjectionNormalizer.RUNTIME_REQUIRED.equals(star.getSqlProjectionStatus()),
            "SELECT * must remain runtime-required");
    }

    private static void assertRealXmlBindings(File xml) throws Exception {
        Document doc = read(xml);
        FieldLineageInfo issue13 = extract(doc, "MP_STAGE_LIS_AML_LOSE_EFFICACY");
        require(issue13 != null, "issue 13 mapping not found in real XML fixture");
        MappingFieldBinding first = issue13.findBinding(null, "COMCODE");
        MappingFieldBinding second = issue13.findBinding(null, "MANAGECOM");
        require(first != null && second != null && first.isProven() && second.isProven(),
            "issue 13 connector/type bindings are not proven");
        require(!first.getReadField().equalsIgnoreCase(second.getReadField()),
            "issue 13 duplicate JDBC identities were retained");
        String issue13Sql = InfaSqlProjectionNormalizer.normalize(mappingSql(doc, "MP_STAGE_LIS_AML_LOSE_EFFICACY"), issue13);
        require(issue13Sql.contains("AS COMCODE") && issue13Sql.contains("AS MANAGECOM")
            && InfaSqlProjectionNormalizer.PROVEN.equals(issue13.getSqlProjectionStatus()),
            "issue 13 real SQL did not receive unique connector aliases");

        FieldLineageInfo literal = extract(doc, "MP_STAGE_CEM_V_TOAUDITING_INVOICE_D");
        String literalSql = InfaSqlProjectionNormalizer.normalize(mappingSql(doc, "MP_STAGE_CEM_V_TOAUDITING_INVOICE_D"), literal);
        require(literalSql.contains("0.0000 AS TAXRATE"), "issue 10 literal was not bound by connector order");

        FieldLineageInfo expressions = extract(doc, "MP_STAGE_AGENTCHNL_SYS_MENU_OPTION");
        String expressionSql = InfaSqlProjectionNormalizer.normalize(mappingSql(doc, "MP_STAGE_AGENTCHNL_SYS_MENU_OPTION"), expressions);
        require(expressionSql.contains("AS T2_COM_CODE") && expressionSql.contains("AS T2_COM_NAME"),
            "issue 10 unaliased expressions were not bound by connector order");

        FieldLineageInfo suffix = extract(doc, "MP_STAGE_TRAIN_FALSE_TRAINING");
        InfaSqlProjectionNormalizer.normalize(mappingSql(doc, "MP_STAGE_TRAIN_FALSE_TRAINING"), suffix);
        require(InfaSqlProjectionNormalizer.PREFIX_PROVEN.equals(suffix.getSqlProjectionStatus())
            && !suffix.isReaderFieldAvailable("COINCIDE_TRAINCODE"),
            "issue 10 unresolved SQL suffix was silently promoted");

        int proven = 0;
        for (MappingFieldBinding binding : issue13.getFieldBindings(null)) {
            if (!binding.isProven()) continue;
            proven++;
            require(binding.getReadMeta().toSqlFieldBean(binding.getReadField()).getType() != 0,
                "real XML read type serialized as 0: " + binding.getReadField());
            require(binding.getWriteMeta().toSqlFieldBean(binding.getTargetField()).getType() != 0,
                "real XML write type serialized as 0: " + binding.getTargetField());
        }
        require(proven > 0, "real XML fixture produced no proven typed bindings");
    }

    private static void assertApiClassification(File lookupXml, File apiXml) throws Exception {
        Method classifier = DataSyncParser.class.getDeclaredMethod("isExternalApiTransformation", Element.class);
        classifier.setAccessible(true);
        int lookups = 0;
        Document lookupDoc = read(lookupXml);
        for (Element transformation : elements(lookupDoc, "TRANSFORMATION")) {
            if ("Lookup Procedure".equals(transformation.getAttribute("TYPE"))) {
                lookups++;
                require(!((Boolean)classifier.invoke(null, transformation)).booleanValue(),
                    "Lookup Procedure was classified as an external API: " + transformation.getAttribute("NAME"));
            }
        }
        require(lookups == 6, "expected six Lookup Procedure regression fixtures, got " + lookups);
        Method extractor = DataSyncParser.class.getDeclaredMethod("extractApiConfig", Document.class, Element.class);
        extractor.setAccessible(true);
        Element lookupSession = null;
        for (Element session : elements(lookupDoc, "SESSION")) {
            if ("MP_STAGE_QD_LCCONT_SELF_NOTPAID".equals(session.getAttribute("MAPPINGNAME"))) {
                lookupSession = session;
                break;
            }
        }
        require(lookupSession != null && extractor.invoke(null, lookupDoc, lookupSession) == null,
            "Lookup-only mapping still generated an external API configuration");
        boolean positive = false;
        Document apiDoc = read(apiXml);
        for (Element transformation : elements(apiDoc, "TRANSFORMATION")) {
            if (((Boolean)classifier.invoke(null, transformation)).booleanValue()) {
                positive = true;
                break;
            }
        }
        require(positive, "genuine Java/SOAP external-call fixture was no longer detected");
    }

    private static FieldLineageInfo extract(Document doc, String mapping) throws Exception {
        Method method = DataSyncParser.class.getDeclaredMethod("extractFieldLineageInfo", Document.class, String.class);
        method.setAccessible(true);
        return (FieldLineageInfo)method.invoke(null, doc, mapping);
    }

    private static String mappingSql(Document doc, String mappingName) {
        for (Element mapping : elements(doc, "MAPPING")) {
            if (!mappingName.equals(mapping.getAttribute("NAME"))) continue;
            NodeList attributes = mapping.getElementsByTagName("TABLEATTRIBUTE");
            for (int i = 0; i < attributes.getLength(); i++) {
                Element attribute = (Element)attributes.item(i);
                if ("Sql Query".equals(attribute.getAttribute("NAME"))
                    && attribute.getAttribute("VALUE").trim().length() > 0) {
                    return attribute.getAttribute("VALUE");
                }
            }
        }
        throw new IllegalStateException("mapping SQL not found: " + mappingName);
    }

    private static Document read(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(file);
    }

    private static List<Element> elements(Document doc, String tag) {
        List<Element> result = new ArrayList<Element>();
        NodeList nodes = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element)nodes.item(i));
        return result;
    }

    private static FieldLineageInfo projection(String... names) {
        FieldLineageInfo info = new FieldLineageInfo();
        int ordinal = 0;
        for (String name : names) {
            InfaFieldMeta meta = meta(name, InfaFieldMeta.KIND_TRANSFORM, "string", 100, 0);
            meta.setOrdinal(++ordinal);
            info.addReaderProjectionField("reader", meta);
        }
        return info;
    }

    private static MappingFieldBinding binding(String targetInstance, String targetField, String readField,
                                               InfaFieldMeta readMeta, InfaFieldMeta writeMeta) {
        MappingFieldBinding binding = new MappingFieldBinding();
        binding.setTargetInstance(targetInstance);
        binding.setTargetDefinition(targetInstance);
        binding.setTargetOrdinal(1);
        binding.setTargetField(targetField);
        binding.setProducerInstance("producer");
        binding.setProducerType("Expression");
        binding.setProducerOrdinal(1);
        binding.setReadField(readField);
        binding.setReadMeta(readMeta);
        binding.setWriteMeta(writeMeta);
        binding.setStatus(MappingFieldBinding.PROVEN);
        return binding;
    }

    private static InfaFieldMeta meta(String name, String kind, String type, int precision, int scale) {
        InfaFieldMeta meta = new InfaFieldMeta();
        meta.setInstanceName("fixture");
        meta.setFieldName(name);
        meta.setFieldKind(kind);
        meta.setOrdinal(1);
        meta.setDataType(type);
        meta.setPrecision(precision);
        meta.setScale(scale);
        meta.setNullable(false);
        return meta;
    }

    private static void assertType(InfaFieldMeta meta, int jdbcType, String typeName) {
        SqlFieldBean field = meta.toSqlFieldBean(meta.getFieldName());
        require(field.getType() == jdbcType && typeName.equals(field.getTypeName())
            && field.getSize() == meta.getPrecision() && field.getScale() == meta.getScale(),
            "type mapping mismatch for " + meta.getDataType());
    }

    private static int count(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
