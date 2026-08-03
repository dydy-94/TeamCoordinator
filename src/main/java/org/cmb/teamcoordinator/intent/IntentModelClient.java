package org.cmb.teamcoordinator.intent;

public interface IntentModelClient {

    String modelName();

    String analyze(String prompt, IntentAnalysisContext context);

    String repair(String prompt, String invalidOutput, IntentAnalysisContext context);
}
