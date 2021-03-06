package hudson.plugins.octopusdeploy;

import com.octopusdeploy.api.OctopusApi;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * @author wbenayed
 */
public class OctopusDeployServer implements Serializable {
    // This value should be incremented every time that this serializable's contract changes
    private static final long serialVersionUID = 1;
            
    private final boolean isDefault;
    public boolean isDefault() {
        return isDefault;
    }

    private String id;
    public String getId() {
        return id;
    }

    private String url;
    public String getUrl() {
        return url;
    }

    private String apiKey;
    public String getApiKey() {
        return apiKey;
    }

    private transient OctopusApi api;
    public OctopusApi getApi() {
        ///TODO use better approach to achieve Laziness
        if (api == null) {
            api = new OctopusApi(url, apiKey);
        }
        return api;
    }

    public OctopusDeployServer(String serverId, String url, String apiKey, boolean isDefault) {
        this.id = serverId.trim();
        this.url = url.trim();
        this.apiKey = apiKey.trim();
        this.isDefault = isDefault;
    }

    @DataBoundConstructor
    public OctopusDeployServer(String serverId, String url, String apiKey) {
        this(serverId, url, apiKey, false);
    }
}
