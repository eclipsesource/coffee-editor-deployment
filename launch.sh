#!/bin/sh

echo Echo environment 1.COFFEE_EDITOR 2.MAX_CONCURRENT_USERS 3.MIN_AVAILABLE_SERVICES 4.JOB_CLEANUP_MINUTES 5.JOB_TTL_SECONDS 6.CLEANUP_HOURS 7.DEBUGGING 8.INGRESS_LIMIT 9.EGRESS_LIMIT 10.PARALLEL_SPAWN
echo $COFFEE_EDITOR
echo $MAX_CONCURRENT_USERS
echo $MIN_AVAILABLE_SERVICES
echo $JOB_CLEANUP_MINUTES
echo $JOB_TTL_SECONDS
echo $CLEANUP_HOURS
echo $DEBUGGING
echo $INGRESS_LIMIT;
echo $EGRESS_LIMIT;
echo $PARALLEL_SPAWN;

java -Declipse.ignoreApp=true -Dosgi.noShutdown=true -Dorg.osgi.service.http.port=9091 -Dorg.eclipse.rwt.clientLibraryVariant=DEBUG -Dorg.eclipse.equinox.http.jetty.log.stderr.threshold=info -Dcoffee.editor=$COFFEE_EDITOR -Dmax.concurrent.users=$MAX_CONCURRENT_USERS -Dmin.available.services=$MIN_AVAILABLE_SERVICES -Djob.cleanup.minutes=$JOB_CLEANUP_MINUTES -Djob.ttl.seconds=$JOB_TTL_SECONDS -Dcleanup.hours=$CLEANUP_HOURS -Dingress.limit=$INGRESS_LIMIT -Degress.limit=$EGRESS_LIMIT -Dparallel.spawn=$PARALLEL_SPAWN -Ddebugging=$DEBUGGING -jar /coffee-editor//plugins/org.eclipse.equinox.launcher_1.5.500.v20190715-1310.jar -os linux -ws gtk -arch -showsplash -name Eclipse -startup /coffee-editor//plugins/org.eclipse.equinox.launcher_1.5.500.v20190715-1310.jar --launcher.overrideVmargs -exitdata 888021 -consoleLog -console 
