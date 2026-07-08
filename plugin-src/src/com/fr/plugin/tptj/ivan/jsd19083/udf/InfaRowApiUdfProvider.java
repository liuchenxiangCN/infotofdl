package com.fr.plugin.tptj.ivan.jsd19083.udf;

import com.fr.dp.spark.udf.SparkUDFPluginSign;
import com.fr.plugin.dp.spark.udf.AbstractSparkUDFPluginProvider;

import java.util.ArrayList;
import java.util.List;

public class InfaRowApiUdfProvider extends AbstractSparkUDFPluginProvider {
    @Override
    public List<SparkUDFPluginSign<?>> getUDFSigns() {
        List<SparkUDFPluginSign<?>> signs = new ArrayList<SparkUDFPluginSign<?>>();
        signs.add(new InfaSoapHttpStatusUdf());
        return signs;
    }
}
