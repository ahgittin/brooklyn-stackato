package brooklyn.example.cloudfoundry

import groovy.lang.MetaClass
import groovy.transform.InheritConstructors

import java.util.Collection
import java.util.Map;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractConfigurableEntityFactory;
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.entity.trait.Startable
import brooklyn.entity.trait.StartableMethods
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.task.BasicExecutionManager;

import com.google.common.collect.Iterables

@InheritConstructors
class MovableElasticWebAppCluster extends AbstractEntity implements Startable, MovableEntityTrait, ElasticJavaWebAppService {

    public static final Logger log = LoggerFactory.getLogger(MovableElasticWebAppCluster.class);

    @SetFromFlag("ttl")
    public static final BasicConfigKey<Long> TIME_TO_LIVE_SECONDS =
        [ Long, "movable.time.to.live", "Time to keep demoted cluster alive (should exceed GeoDNS TTL; default 0)", 0 ];

    public static final BasicAttributeSensor<String> PRIMARY_SVC_ENTITY_ID = 
        [ String, "movable.primary.id", "Entity ID of primary web-app service" ];
    public static final BasicAttributeSensor<Collection<String>> SECONDARY_SVC_ENTITY_IDS = 
        [ Collection, "movable.secondary.ids", "Entity IDs of secondary web-app services" ];
    
    @Override
    public void start(Collection<? extends Location> locations) {
        if (!getOwnedChildren().isEmpty()) {
            log.debug("Starting $this; it already has children, so start on children is being invoked")
            StartableMethods.start(this, locations);
        } else {
            Entity svc = createClusterIn( Iterables.getOnlyElement(locations) );
            log.debug("Starting $this; no children, so created $svc and now starting it")
            if (svc in Startable) ((Startable)svc).start(locations);
            setPrimary(svc);
        }
    }
    
    SensorPropagatingEnricher primaryEnricher = null;
    protected synchronized void setPrimary(Entity svc) {
        if (primaryEnricher!=null)
            removeEnricher(primaryEnricher);
        setAttribute(PRIMARY_SVC_ENTITY_ID, svc.id);
        primaryEnricher = SensorPropagatingEnricher.newInstanceListeningToAllSensors(svc);
        addEnricher(primaryEnricher);
        primaryEnricher.emitAllAttributes(true);
    }

    public EntityLocal createClusterIn(Location location) {
        //TODO the policy
//        app.web.cluster.addPolicy(app.policy)
        return new ElasticJavaWebAppService.Factory().
            newFactoryForLocation(location).
            newEntity([:], this);
    }
    
    @Override
    public void stop() {
        StartableMethods.stop(this);
    }

    @Override
    public void restart() {
        StartableMethods.restart(this);
    }

    /* 
     * "move" consists of creating a secondary (call it Y),
     * promoting this one (Y) swapping it for the old-primary (call it X),
     * then destroying the old-primary-now-secondary (X)
     */

    public static final Effector<String> CREATE_SECONDARY_IN_LOCATION = new MethodEffector<String>(this.&createSecondaryInLocation);
    public static final Effector<String> PROMOTE_SECONDARY = new MethodEffector<String>(this.&promoteSecondary);
    public static final Effector<String> DESTROY_SECONDARY = new MethodEffector<String>(this.&destroySecondary);
    
    /** creates a new secondary instance, in the given location, returning the ID of the secondary created and started */
    @Description("create a new secondary instance in the given location")
    public String createSecondaryInLocation(
            @NamedParameter("location") @Description("the location where to start the secondary")
            String l) {
        Location location = new LocationRegistry().resolve(l);
        Entity svc = createClusterIn(location);
        log.info("Creating and starting secondary web cluster "+svc);
        ((Startable)svc).start([location]);
//        Entities.start(managementContext, svc, [location]);
        setAttribute(SECONDARY_SVC_ENTITY_IDS, (getAttribute(SECONDARY_SVC_ENTITY_IDS) ?: []) + svc.id);
        return svc.id;
    }

    /** promotes the indicated secondary,
     * returning the ID of the former-primary which has been demoted */
    @Description("promote the indicated secondary to primary (demoting the existing primary)")
    public String promoteSecondary(
            @NamedParameter("idOfSecondaryToPromote") @Description("ID of secondary entity to promote")
            String idOfSecondaryToPromote) {
        Collection<String> currentSecondaryIds = getAttribute(SECONDARY_SVC_ENTITY_IDS)
        if (!currentSecondaryIds.contains(idOfSecondaryToPromote)) 
            throw new IllegalStateException("Cannot promote unknown secondary $idOfSecondaryToPromote "+
                "(available secondaries are $currentSecondaryIds)");
            
        String primaryId = getAttribute(PRIMARY_SVC_ENTITY_ID);
        
        log.info("Promoting secondary web cluster "+idOfSecondaryToPromote+" (demoting "+primaryId+")");
        
        setAttribute(PRIMARY_SVC_ENTITY_ID, idOfSecondaryToPromote);
        currentSecondaryIds.remove(idOfSecondaryToPromote);
        currentSecondaryIds << primaryId;
        setAttribute(SECONDARY_SVC_ENTITY_IDS, currentSecondaryIds);
        return primaryId;
    }
    
    /** destroys the indicated secondary */
    @Description("destroy the indicated secondary")
    public void destroySecondary(
            @NamedParameter("idOfSecondaryToDestroy") @Description("ID of secondary entity to destroy")
            String idOfSecondaryToDestroy) {
        Collection<String> currentSecondaryIds = getAttribute(SECONDARY_SVC_ENTITY_IDS)
        if (!currentSecondaryIds.contains(idOfSecondaryToDestroy))
            throw new IllegalStateException("Cannot promote unknown secondary $idOfSecondaryToDestroy "+
                "(available secondaries are $currentSecondaryIds)");
            
        currentSecondaryIds.remove(idOfSecondaryToDestroy);
        setAttribute(SECONDARY_SVC_ENTITY_IDS, currentSecondaryIds);
        
        Entity secondary = getManagementContext().getEntity(idOfSecondaryToDestroy);
        log.info("Destroying secondary web cluster "+secondary);
        Entities.destroy(managementContext, secondary);
    }

    @Override
    public String move(String location) {
        String newPrimary = createSecondaryInLocation(location);
        String oldPrimary = promoteSecondary(newPrimary);
        long ttl = getConfig(TIME_TO_LIVE_SECONDS);
        if (ttl>0)
            BasicExecutionManager.withBlockingDetails("waiting for TTL to destroy old primary") { Thread.sleep(ttl*1000); }
        destroySecondary(oldPrimary);
        return newPrimary;
    }

    public static class Factory extends AbstractConfigurableEntityFactory<ElasticJavaWebAppService> {
        public ElasticJavaWebAppService newEntity2(Map flags, Entity owner) {
            new MovableElasticWebAppCluster(flags, owner);
        }
    }

}
