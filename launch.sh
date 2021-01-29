#!/bin/sh

echo Echo environment 1.NAMESPACE 2.IMAGE_NAME 3.IMAGE_VERSION 4.IMAGE_REGISTRY 5.MAX_CONCURRENT_USERS 6.MIN_AVAILABLE_SERVICES 7.JOB_CLEANUP_MINUTES 8.JOB_TTL_SECONDS 9.CLEANUP_HOURS 10.DEBUGGING 11.INGRESS_LIMIT 12.EGRESS_LIMIT 13.PARALLEL_SPAWN 14.HOST 15.PREFIX 16.IMAGE_VERSION_PREVIEW
echo $NAMESPACE
echo $IMAGE_NAME
echo $IMAGE_VERSION
echo $IMAGE_REGISTRY
echo $MAX_CONCURRENT_USERS
echo $MIN_AVAILABLE_SERVICES
echo $JOB_CLEANUP_MINUTES
echo $JOB_TTL_SECONDS
echo $CLEANUP_HOURS
echo $DEBUGGING
echo $INGRESS_LIMIT;
echo $EGRESS_LIMIT;
echo $PARALLEL_SPAWN;
echo $HOST;
echo $PREFIX;
echo $IMAGE_VERSION_PREVIEW

java -Declipse.ignoreApp=true -Dosgi.noShutdown=true -Dorg.osgi.service.http.port=9091 -Dorg.eclipse.rwt.clientLibraryVariant=DEBUG -Dorg.eclipse.equinox.http.jetty.log.stderr.threshold=info -Dnamespace=$NAMESPACE -Dimage.name=$IMAGE_NAME -Dimage.version=$IMAGE_VERSION -Dimage.version.preview=$IMAGE_VERSION_PREVIEW -Dimage.registry=$IMAGE_REGISTRY -Dmax.concurrent.users=$MAX_CONCURRENT_USERS -Dmin.available.services=$MIN_AVAILABLE_SERVICES -Djob.cleanup.minutes=$JOB_CLEANUP_MINUTES -Djob.ttl.seconds=$JOB_TTL_SECONDS -Dcleanup.hours=$CLEANUP_HOURS -Dingress.limit=$INGRESS_LIMIT -Degress.limit=$EGRESS_LIMIT -Dparallel.spawn=$PARALLEL_SPAWN -Ddebugging=$DEBUGGING -Dhost=$HOST -Dprefix=$PREFIX -jar /coffee-editor//plugins/org.eclipse.equinox.launcher_1.5.500.v20190715-1310.jar -os linux -ws gtk -arch -showsplash -name Eclipse -startup /coffee-editor//plugins/org.eclipse.equinox.launcher_1.5.500.v20190715-1310.jar --launcher.overrideVmargs -exitdata 888021 -consoleLog -console 
