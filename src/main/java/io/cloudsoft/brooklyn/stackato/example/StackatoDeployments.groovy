package io.cloudsoft.brooklyn.stackato.example


import io.cloudsoft.brooklyn.stackato.StackatoDeployment;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient;
import brooklyn.location.basic.LocationRegistry
import groovy.transform.InheritConstructors;

@InheritConstructors
class StackatoDeployments extends AbstractEntity {

    public static Effector<Void> NEW_STACKATO_DEPLOYMENT = new MethodEffector(this.&newStackatoDeployment);
    
    @Description("Start a Stackato cluster in the indicated location")
    public String newStackatoDeployment(@NamedParameter("location") String location) {
        StackatoDeployment stackato = new StackatoDeployment(this,
                domain: "geopaas.org",
                admin: "me@fun.net",
                password: "funfunfun"
                );
        stackato.configure(cluster: "brooklyn-stackato-"+stackato.id);
        stackato.useDnsClient(dnsClient);
        stackato.start([new LocationRegistry().resolve(location)]);
        return stackato.id;
    }

    private GeoscalingWebClient dnsClient;
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
    public void useDnsClient(GeoscalingWebClient dnsClient) {
        this.dnsClient = dnsClient;
    }

}
