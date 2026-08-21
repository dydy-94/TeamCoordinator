package org.cmb.application.service;

import org.cmb.application.domain.IntentAnalysisContext;

public interface IntentModelClient {

    String modelName();

    String analyze(String prompt, IntentAnalysisContext context);

    String repair(String prompt, String invalidOutput, IntentAnalysisContext context);
}
