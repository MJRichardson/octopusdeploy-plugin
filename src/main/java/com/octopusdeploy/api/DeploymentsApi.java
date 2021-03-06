package com.octopusdeploy.api;

import com.octopusdeploy.api.data.DeploymentProcess;
import com.octopusdeploy.api.data.DeploymentProcessStep;
import com.octopusdeploy.api.data.DeploymentProcessStepAction;
import com.octopusdeploy.api.data.DeploymentProcessTemplate;
import com.octopusdeploy.api.data.SelectedPackage;
import com.octopusdeploy.api.data.Variable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;

public class DeploymentsApi {
    private final static String UTF8 = "UTF-8";
    private final AuthenticatedWebClient webClient;

    public DeploymentsApi(AuthenticatedWebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Deploys a given release to provided environment.
     * @param releaseId Release Id from Octopus to deploy.
     * @param environmentId Environment Id from Octopus to deploy to.
     * @param tenantId Tenant Id from Octopus to deploy to.
     * @return the content of the web response.
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public String executeDeployment(String releaseId, String environmentId, String tenantId) throws IOException {
        return executeDeployment( releaseId,  environmentId, tenantId, null);
    }

    /**
     * Deploys a given release to provided environment.
     * @param releaseId Release Id from Octopus to deploy.
     * @param environmentId Environment Id from Octopus to deploy to.
     * @param tenantId Tenant Id from Octopus to deploy to.
     * @param variables Variables used during deployment.
     * @return the content of the web response.
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public String executeDeployment(String releaseId, String environmentId, String tenantId, Set<Variable> variables) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append(String.format("{EnvironmentId:\"%s\",ReleaseId:\"%s\"", environmentId, releaseId));

        if (tenantId != null && !tenantId.isEmpty()) {
            jsonBuilder.append(String.format(",TenantId:\"%s\"", tenantId));
        }
        if (variables != null && !variables.isEmpty()) {
            jsonBuilder.append(",FormValues:{");
            Set<String> variablesStrings = new HashSet<String>();
            for (Variable v : variables) {
                variablesStrings.add(String.format("\"%s\":\"%s\"", v.getId(), v.getValue()));
            }
            jsonBuilder.append(StringUtils.join(variablesStrings, ","));
            jsonBuilder.append("}");
        }
        jsonBuilder.append("}");
        String json = jsonBuilder.toString();

        byte[] data = json.getBytes(Charset.forName(UTF8));
        AuthenticatedWebClient.WebResponse response = webClient.post("api/deployments", data);
        if (response.isErrorCode()) {
            String errorMsg = ErrorParser.getErrorsFromResponse(response.getContent());
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), errorMsg));
        }
        return response.getContent();
    }
    
    /**
     * Return a representation of a deployment process for a given project.
     * @param projectId the id of the project to get the process for.
     * @return DeploymentProcess a representation of the process
     * @throws IllegalArgumentException when the web client receives a bad parameter
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public DeploymentProcess getDeploymentProcessForProject(String projectId) throws IllegalArgumentException, IOException {
        // TODO: refactor/method extract/clean up
        AuthenticatedWebClient.WebResponse response = webClient.get("api/deploymentprocesses/deploymentprocess-" + projectId);
        if (response.isErrorCode()) {
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), response.getContent()));
        }
        JSONObject json = (JSONObject)JSONSerializer.toJSON(response.getContent());
        JSONArray stepsJson = json.getJSONArray("Steps");
        HashSet<DeploymentProcessStep> deploymentProcessSteps = new HashSet<DeploymentProcessStep>();
        for (Object stepObj : stepsJson) {
            JSONObject jsonStepObj = (JSONObject)stepObj;
            HashSet<DeploymentProcessStepAction> deploymentProcessStepActions = new HashSet<DeploymentProcessStepAction>();

            JSONArray actionsJson = jsonStepObj.getJSONArray("Actions");
            for (Object actionObj : actionsJson) {
                JSONObject jsonActionObj = (JSONObject)actionObj;
                JSONObject propertiesJson = jsonActionObj.getJSONObject("Properties");
                HashMap<String, String> properties = new HashMap<String, String>();
                for (Object key : propertiesJson.keySet()) {
                    String keyString = key.toString();
                    properties.put(keyString, propertiesJson.getString(keyString));
                }
                String dpsaId = jsonActionObj.getString("Id");
                String dpsaName = jsonActionObj.getString("Name");
                String dpsaType = jsonActionObj.getString("ActionType");
                deploymentProcessStepActions.add(new DeploymentProcessStepAction(dpsaId, dpsaName, dpsaType, properties));
            }
            String dpsId = jsonStepObj.getString("Id");
            String dpsName = jsonStepObj.getString("Name");
            deploymentProcessSteps.add(new DeploymentProcessStep(dpsId, dpsName, deploymentProcessStepActions));
        }
        String dpId = json.getString("Id");
        String dpProject = json.getString("ProjectId");
        return new DeploymentProcess(dpId, dpProject, deploymentProcessSteps);
    }

    /**
     * Return a representation of a deployment process for a given project.
     * @param projectId project id
     * @return DeploymentProcessTemplate deployment process template
     * @throws IllegalArgumentException when the web client receives a bad parameter
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public DeploymentProcessTemplate getDeploymentProcessTemplateForProject(String projectId) throws IllegalArgumentException, IOException {
        AuthenticatedWebClient.WebResponse response = webClient.get("api/deploymentprocesses/deploymentprocess-" + projectId + "/template");
        if (response.isErrorCode()) {
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), response.getContent()));
        }

        JSONObject json = (JSONObject)JSONSerializer.toJSON(response.getContent());
        Set<SelectedPackage> packages = new HashSet<SelectedPackage>();
        String deploymentId = json.getString("DeploymentProcessId");
        JSONArray pkgsJson = json.getJSONArray("Packages");
        for (Object pkgObj : pkgsJson) {
            JSONObject pkgJsonObj = (JSONObject) pkgObj;
            String name = pkgJsonObj.getString("StepName");
            String packageId = pkgJsonObj.getString("PackageId");
            String packageReferenceName = pkgJsonObj.getString("PackageReferenceName");
            String version = pkgJsonObj.getString("VersionSelectedLastRelease");
            packages.add(new SelectedPackage(name, packageId, packageReferenceName, version));
        }

        DeploymentProcessTemplate template = new DeploymentProcessTemplate(deploymentId, projectId, packages);
        return template;
    }
}
