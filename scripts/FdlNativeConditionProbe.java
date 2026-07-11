import com.fr.dp.component.domain.entities.ConditionComponent;
import com.fr.dp.component.domain.entities.entity.DPConditionItem;
import com.fr.dp.component.infrastructure.ConditionItemCreator;
import com.fr.dp.condition.entity.AndOrConditionEntity;
import com.fr.dp.condition.entity.CompareConditionEntity;
import com.fr.dp.condition.entity.ConditionType;
import com.fr.dp.condition.entity.DPConditionEntity;
import com.fr.dp.condition.entity.value.SelfConditionEntity;
import com.fr.dp.condition.type.ConditionCompareType;
import com.fr.dp.condition.type.ConditionValueType;
import com.fr.dp.param.DPParam;
import com.fr.dp.param.embed.DefaultEmbeddedParam;
import com.fr.dp.scheduler.distributed.master.metadata.Param;
import com.fr.dp.scheduler.distributed.worker.executor.param.ExecutorParam;
import com.fr.dp.util.JsonUtil;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.ConditionNodeBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FdlNativeConditionProbe {
    private static final List<String> TASKS = Arrays.asList("task_a", "task_b", "task_c");

    public static void main(String[] args) {
        ConditionComponent trueBranch = decisionBranch("true");
        assertDecisionEntity(trueBranch, ConditionType.AND, ConditionCompareType.BELONGS);
        assertSerializedNativeEntity(trueBranch);
        assertMatch(trueBranch, Arrays.asList("success", "success", "success"), true);
        assertMatch(trueBranch, Arrays.asList("success", "fail", "success"), false);
        assertMatch(trueBranch, Arrays.asList("success", "skipped", "success"), false);

        ConditionComponent falseBranch = decisionBranch("false");
        assertDecisionEntity(falseBranch, ConditionType.OR, ConditionCompareType.NOT_BELONGS);
        assertMatch(falseBranch, Arrays.asList("success", "success", "success"), false);
        assertMatch(falseBranch, Arrays.asList("success", "fail", "success"), true);
        assertMatch(falseBranch, Arrays.asList("success", "skipped", "success"), true);

        assertCompare("name = 'success'", ConditionValueType.STRING, ConditionCompareType.BELONGS);
        assertCompare("name != 'success'", ConditionValueType.STRING, ConditionCompareType.NOT_BELONGS);
        assertCompare("row_count = 1", ConditionValueType.NUMBER, ConditionCompareType.BELONGS);
        assertBoolean("enabled = true", ConditionCompareType.IS_TRUE);
        assertBoolean("enabled = false", ConditionCompareType.IS_FALSE);
        assertCompare("NOT (name = 'success')", ConditionValueType.STRING, ConditionCompareType.NOT_BELONGS);
        assertBoolean("NOT (enabled = true)", ConditionCompareType.IS_FALSE);
        assertNestedNot();
        assertUnsupported("name > 'a'");

        System.out.println("PASS FDL native condition probe: entity, render, AND/OR, true/false, fail/skipped");
    }

    private static ConditionComponent decisionBranch(String expectedValue) {
        ConditionNodeBean bean = new ConditionNodeBean(
            0, 0, "decision", "decision-id", "native probe", true, "decision", "strict success-AND", TASKS
        );
        bean.addBranch("next-id", "next", "$decision.Condition = " + expectedValue);
        return (ConditionComponent) bean.getFDLComponent();
    }

    private static void assertDecisionEntity(ConditionComponent component, ConditionType logic, ConditionCompareType compareType) {
        DPConditionItem item = component.getConditionItems().get(0);
        DPConditionEntity entity = item.getConditionEntity();
        if (!(entity instanceof AndOrConditionEntity)) {
            fail("expected AndOrConditionEntity, actual=" + entity);
        }
        AndOrConditionEntity logical = (AndOrConditionEntity) entity;
        if (logical.getConditionType() != logic || logical.getConditions().size() != TASKS.size()) {
            fail("logical condition mismatch: type=" + logical.getConditionType() + ", size=" + logical.getConditions().size());
        }
        for (int i = 0; i < logical.getConditions().size(); i++) {
            CompareConditionEntity child = (CompareConditionEntity) logical.getConditions().get(i);
            String source = String.valueOf(child.getSource().getValue());
            if (child.getValueType() != ConditionValueType.STRING
                || child.getConditionCompareType() != compareType
                || !("${" + TASKS.get(i) + ".opresult}").equals(source)
                || !"success".equals(String.valueOf(child.getTarget().getValue()))) {
                fail("child condition mismatch at " + i + ": " + child);
            }
        }
    }

    private static void assertSerializedNativeEntity(ConditionComponent component) {
        String json = JsonUtil.toJsonString(component.toEntity());
        String[] required = {
            "\"conditionEntity\"", "\"conditionType\":\"AND\"", "\"type\":\"andor\"",
            "\"conditionCompareType\":\"BELONGS\"", "\"valueType\":\"STRING\"",
            "${task_a.opresult}", "${task_b.opresult}", "${task_c.opresult}"
        };
        for (String token : required) {
            if (!json.contains(token)) {
                fail("serialized native entity missing token " + token + ": " + json);
            }
        }
    }

    private static void assertMatch(ConditionComponent component, List<String> results, boolean expected) {
        List<DPParam> params = new ArrayList<DPParam>();
        for (int i = 0; i < TASKS.size(); i++) {
            Param param = new Param(
                TASKS.get(i) + ".opresult", DefaultEmbeddedParam.of(results.get(i)), "EMBED"
            );
            params.add(new ExecutorParam(param));
        }
        ConditionComponent rendered = (ConditionComponent) component.executeRender(params);
        boolean actual = ConditionItemCreator.create(rendered.getConditionItems())
            .get(0).getExecutorCondition().satisfied();
        if (actual != expected) {
            fail("execution mismatch: results=" + results + ", expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertCompare(String expression, ConditionValueType valueType, ConditionCompareType compareType) {
        DPConditionEntity entity = ConditionNodeBean.analyzeCondition(expression).getConditionEntity();
        if (!(entity instanceof CompareConditionEntity)) {
            fail("expected CompareConditionEntity for " + expression + ", actual=" + entity);
        }
        CompareConditionEntity compare = (CompareConditionEntity) entity;
        if (compare.getValueType() != valueType || compare.getConditionCompareType() != compareType) {
            fail("native compare mismatch for " + expression + ": " + compare);
        }
    }

    private static void assertBoolean(String expression, ConditionCompareType compareType) {
        DPConditionEntity entity = ConditionNodeBean.analyzeCondition(expression).getConditionEntity();
        if (!(entity instanceof SelfConditionEntity)) {
            fail("expected SelfConditionEntity for " + expression + ", actual=" + entity);
        }
        SelfConditionEntity self = (SelfConditionEntity) entity;
        if (self.getValueType() != ConditionValueType.BOOLEAN || self.getConditionCompareType() != compareType) {
            fail("native boolean mismatch for " + expression + ": " + self);
        }
    }

    private static void assertUnsupported(String expression) {
        ConditionNodeBean.ConditionAnalysis analysis = ConditionNodeBean.analyzeCondition(expression);
        if (!ConditionNodeBean.STATUS_UNSUPPORTED.equals(analysis.getStatus()) || analysis.getConditionEntity() != null) {
            fail("unsupported expression was promoted: " + expression + " status=" + analysis.getStatus());
        }
    }

    private static void assertNestedNot() {
        DPConditionEntity entity = ConditionNodeBean.analyzeCondition(
            "NOT (name = 'success' AND row_count > 1)"
        ).getConditionEntity();
        if (!(entity instanceof AndOrConditionEntity)) {
            fail("expected native De Morgan entity, actual=" + entity);
        }
        AndOrConditionEntity logical = (AndOrConditionEntity) entity;
        if (logical.getConditionType() != ConditionType.OR || logical.getConditions().size() != 2) {
            fail("native De Morgan mismatch: " + logical);
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
