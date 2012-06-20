package io.cloudsoft.brooklyn.stackato;

import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface StackatoConfigKeys {

    @SetFromFlag("cluster")
    public static BasicConfigKey<String> STACKATO_CLUSTER_NAME = new BasicConfigKey<String>(String.class, "stackato.cluster.name", "a unique name for the cluster, e.g. 'my1' if URL is to be api.my1.example.com");
    @SetFromFlag("domain")
    public static BasicConfigKey<String> STACKATO_CLUSTER_DOMAIN = new BasicConfigKey<String>(String.class, "stackato.cluster.domain", "base domain name for the cluster, excluding cluster name, e.g. 'example.com' if URL is to be api.my1.example.com");
    
    @SetFromFlag("admin")
    public static BasicConfigKey<String> STACKATO_ADMIN_USER_EMAIL = new BasicConfigKey<String>(String.class, "stackato.admin", "", "stackato");
    @SetFromFlag("password")
    public static BasicConfigKey<String> STACKATO_PASSWORD = new BasicConfigKey<String>(String.class, "stackato.password", "", "st4ck4t0");
    
    @SetFromFlag("minRam")
    public static BasicConfigKey<Integer> MIN_RAM_MB = new BasicConfigKey<Integer>(Integer.class, "stackato.minram", "", 4000);

}
