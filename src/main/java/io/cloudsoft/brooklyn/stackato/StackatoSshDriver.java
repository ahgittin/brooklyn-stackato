package io.cloudsoft.brooklyn.stackato;

import java.io.StringReader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.basic.lifecycle.StartStopSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;

import com.google.common.base.Throwables;

public class StackatoSshDriver extends StartStopSshDriver {

    public static final Logger log = LoggerFactory.getLogger(StackatoSshDriver.class);
    
    StackatoNode node;
    
    public StackatoSshDriver(StackatoNode entity, SshMachineLocation machine) {
        super(entity, machine);
        this.node = entity;
    }

    String getRefreshSudoCommand() { 
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        return "echo "+password+" | sudo -S whoami || exit 1";
    }
    
    @Override
    public boolean isRunning() {
        try {
            newScript("stackato-is-running").
                    failOnNonZeroResultCode().
                    body.append("stackato-admin status | grep RUNNING").
                    execute();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void stop() {
        newScript("stackato-stop").
            failOnNonZeroResultCode().
            body.append("stackato-admin stop").
            execute();
    }

    public void checkMachineHealthy() {
        newScript("stackato-install-check-machine-can-do-root").
            failOnNonZeroResultCode().
            body.append(getRefreshSudoCommand()).
            execute();        
        newScript("stackato-install-check-machine-stackato-status").
            failOnNonZeroResultCode().
            body.append("stackato-admin status").
            execute();
    }

    public String getRequiredConfig(ConfigKey<String> key) {
        String v = node.getConfig(key);
        if (v==null) throw new IllegalStateException("Missing required configuration "+key+"; set on cluster or entity");
        return v;
    }
    
    public void customizeImage() {
        log.info("Customizing Stackato machine at "+getMachine()+" for "+node);
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        String clusterName = getRequiredConfig(StackatoNode.STACKATO_CLUSTER_NAME);
        String clusterDomain = getRequiredConfig(StackatoNode.STACKATO_CLUSTER_DOMAIN);
        
        String internalIp = ((JcloudsSshMachineLocation)getMachine()).getSubnetHostname();
        if (internalIp==null) throw new IllegalStateException("Cannot resolve subnet hostname");
        
        String publicKey = (String)getMachine().getLocationProperty("sshPublicKeyData");
        if (publicKey==null) 
            throw new IllegalStateException("SSH keys required; no public key specified");
        getMachine().copyTo(new StringReader(publicKey), "/tmp/id.pub");
        
        newScript("stackato-customizeImage").
            failOnNonZeroResultCode().
            body.append(
                    // needed if sudo access isn't enabled by default, with password
                    "echo stackato | sudo -S echo sudo access granted || exit 1",
                    // add key for users root (already done) & stackato
                    "cat /tmp/id.pub >> ~stackato/.ssh/authorized_keys || exit 2",
                    // change stackato passwd
                    "echo \""+password+"\n"+password+"\" | sudo -S passwd stackato || exit 3",
                    // setup hosts file (hosts file always points to local machine, but that seems okay?)
                    "sudo bash -c 'echo "+internalIp+" "+clusterName+" "+
                            clusterName+"."+clusterDomain+" "+
                            "api."+clusterName+"."+clusterDomain+" "+
                        " >> /etc/hosts' || exit 4"
            ).execute();
    }
    
    @Override
    public void install() {
        checkMachineHealthy();
        log.info("Confirmed health for installaton of Stackato machine at "+getMachine()+" for "+node);
    }
    
    public void createAdminUser(String username) {
        newScript("stackato-create-admin-user").
            failOnNonZeroResultCode().
            body.append(getRefreshSudoCommand()).
            body.append("stackato-admin admin grant "+username).
            execute();
    }
    
    @Override
    public void customize() {
        node.becomeDesiredStackatoRole();
    }
    
    public void rebootAndWait() {
        log.info("Rebooting machine for "+node);
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        newScript("reboot").
            failOnNonZeroResultCode().
            body.append("echo "+password+" | sudo -S reboot").
            execute();
        try {
            Thread.sleep(30*1000);
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
        log.debug("Rebooted machine for "+node);
    }
    
    @Override
    public void launch() {
        node.blockUntilReadyToLaunch();
        rebootAndWait();
    }

    public String getEndpointHostname() {
        return "api"+"."+
                getRequiredConfig(StackatoNode.STACKATO_CLUSTER_NAME)+"."+
                getRequiredConfig(StackatoNode.STACKATO_CLUSTER_DOMAIN);
    }
    
    public void createAdminUser(String usernameEmail, String password) {
        // http://community.activestate.com/node/8795
        // but we've already changed the unix password so it cannot
        String endpoint = getEndpointHostname();
        int result = execute(Arrays.asList(
                "curl -k https://"+endpoint+"/stackato/license -d \""+
                        "email="+usernameEmail+"&"+
                        "password="+password+"\" > .brooklyn_stackato_user_setup",
                "grep -i error .brooklyn_stackato_user_setup && exit 80 || echo user created"),
                "stackato-admin-user");
        if (result!=0) throw new IllegalStateException("curl to create user failed");
    }

    public void createLicenseFile() {
        String endpoint = getEndpointHostname();
        int result = getMachine().execCommands("stackato-admin-user", Arrays.asList(
                // fine if already created
                "cat .stackato_license 2> /dev/null || ( echo '{\"type\":\"microcloud\"' "+
                        "| curl -k https://"+endpoint+"/stackato/license )",
                "cat .stackato_license"));
        if (result!=0) throw new IllegalStateException("curl to create license failed");
    }

    // curl -k https://api.brooklyn-stackato-hAqiOR9x.geopaas.org/stackato/license -d "email=me@fun.net&password=funfunfun&unix_password=funfunfun"
    
}
