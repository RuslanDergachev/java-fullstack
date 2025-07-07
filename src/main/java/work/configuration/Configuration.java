package work.configuration;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Configuration {
    private String host;
    private int port;
    private String username;
    private String password;
}
