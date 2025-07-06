package work.configuration;

import lombok.experimental.Accessors;

@Accessors(chain = true)
public class Configuration {
    private String host;
    private int port;
    private String username;
    private String password;
}
