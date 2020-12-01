package org.eclipse.emfcloud.coffee.internal.example.k8s;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobBuilder;
import io.kubernetes.client.models.V1JobList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceBuilder;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBuilder;
import io.kubernetes.client.util.ClientBuilder;

@Path("/launch")
@Component(name = "ExampleServer", service = ExampleServer.class, immediate = true)
public class ExampleServer {


	private static String NAMESPACE = "coffee-instance";
	private static String IMAGE_REGISTRY; // Registry to get docker images from
	private static String IMAGE_VERSION; // Docker image's version
	private static String IMAGE_NAME; // Docker image to start as new instances
	private static int MAX_CONCURRENT_USERS;
	private static int MIN_AVAILABLE_SERVICES; // minimum available ips
	private static long JOB_CLEANUP_TIMEFRAME_MINUTES; // timeframe for when to cleanup resources
	private static long ACTIVE_DEADLINE_SECONDS; // minutes until Theia is brought down
	private static long CLEANUP_TIMEFRAME_HOURS; // timeframe for when to cleanup resources when this pod gets evicted
	private static Boolean DEBUGGING;
	private static Boolean PARALLEL_SPAWN;
	private static int INGRESS_LIMIT;
	private static int EGRESS_LIMIT;
	private static String HOST; // The host address that this server runs on
	private static String PREFIX; // prefix of created kubernetes objects (jobs, pods, services, and ingresses)

	private static String getStringProperty(String key, String def) {
		String property = System.getProperty(key);
		if (property == null) {
			log("init", MessageFormat.format("No property for key {0} found. Using {1}", key, def));
			return def;
		}
		log("init", MessageFormat.format("Property for key {0}: {1}", key, property));
		return property;
	}

	private static int getIntProperty(String key, int def) {
		String property = System.getProperty(key);
		if (property == null) {
			log("init", MessageFormat.format("No property for key {0} found. Using {1}", key, def));
			return def;
		}
		try {
			int intValue = Integer.valueOf(property).intValue();
			log("init", MessageFormat.format("Property for key {0}: {1}", key, intValue));
			return intValue;
		} catch (NumberFormatException e) {
			log("init", MessageFormat.format("Could not transform property for key {0}: {2}. Using {1} instead", key,
					def, property));
			return def;
		}
	}

	private static long getLongProperty(String key, long def) {
		String property = System.getProperty(key);
		if (property == null) {
			log("init", MessageFormat.format("No property for key {0} found. Using {1}", key, def));
			return def;
		}
		try {
			long longValue = Long.valueOf(property).longValue();
			log("init", MessageFormat.format("Property for key {0}: {1}", key, longValue));
			return longValue;
		} catch (NumberFormatException e) {
			log("init", MessageFormat.format("Could not transform property for key {0}: {2}. Using {1} instead", key,
					def, property));
			return def;
		}
	}

	private static boolean getBooleanProperty(String key, boolean def) {
		String property = System.getProperty(key);
		if (property == null) {
			log("init", MessageFormat.format("No property for key {0} found. Using {1}", key, def));
			return def;
		}
		try {
			Boolean valueOf = Boolean.valueOf(property);
			log("init", MessageFormat.format("Property for key {0}: {1}", key, valueOf));
			return valueOf;
		} catch (Exception e) {
			log("init", MessageFormat.format("Could not transform property for key {0}: {2}. Using {1} instead", key,
					def, property));
			return def;
		}

	}

	static {
		// Get settings from environment variables
		NAMESPACE= getStringProperty("namespace", "coffee-instance");
		IMAGE_REGISTRY = getStringProperty("image.registry", "eu.gcr.io/kubernetes-238012/");
		IMAGE_NAME = getStringProperty("image.name", "coffee-editor");
		IMAGE_VERSION = getStringProperty("image.version", "0.7.0");
		MAX_CONCURRENT_USERS = getIntProperty("max.concurrent.users", 10);
		MIN_AVAILABLE_SERVICES = getIntProperty("min.available.services", 11);
		JOB_CLEANUP_TIMEFRAME_MINUTES = getLongProperty("job.cleanup.minutes", 15l);
		ACTIVE_DEADLINE_SECONDS = getLongProperty("job.ttl.seconds", 30 * 60l);
		CLEANUP_TIMEFRAME_HOURS = getLongProperty("cleanup.hours", 6);
		DEBUGGING = getBooleanProperty("debugging", false);
		PARALLEL_SPAWN = getBooleanProperty("parallel.spawn", false);
		INGRESS_LIMIT = getIntProperty("ingress.limit", 4);
		EGRESS_LIMIT = getIntProperty("egress.limit", 2);
		HOST = getStringProperty("host", "35.234.81.45.nip.io");
		PREFIX = getStringProperty("prefix", "coffee-editor");
	}

	private Queue<AvailableService> availableServices = new LinkedBlockingQueue<>();
	private Set<String> spawningServiceNames = new CopyOnWriteArraySet<>();
	private int spawningServices = 0;

	private Map<String, AvailableService> servicesInUse = new ConcurrentHashMap<>();

	private final ExecutorService requestSpawner;
	private final ExecutorService serviceSpawner;

	private static class AvailableService {
		String uuid;
		String hostName;

		AvailableService(String uuid, String hostName) {
			this.uuid = uuid;
			this.hostName = hostName;
		}

		public String uuid() {
			return uuid;
		}
	}

	private static class InstanceLaunchException extends Exception {
		public InstanceLaunchException(String msg) {
			super(msg);
		}

		private static final long serialVersionUID = -6425915796611641245L;

	}

	public ExampleServer() {
		if (PARALLEL_SPAWN) {
			requestSpawner = Executors.newFixedThreadPool(MAX_CONCURRENT_USERS);
		} else {
			requestSpawner = Executors.newFixedThreadPool(1);
		}
		serviceSpawner = Executors.newFixedThreadPool(MIN_AVAILABLE_SERVICES);
	}

	private synchronized int incSpawn(int minAvailableServices) {
		int inc = minAvailableServices - spawningServices - availableServices.size();
		if (inc < 1) {
			return 0;
		}
		spawningServices += inc;
		return inc;
	}

	private synchronized void decSpawn() {
		spawningServices -= 1;
	}

	private synchronized void addAvailableServiceAndDecSpawn(String uuid, String hostName) {
		availableServices.add(new AvailableService(uuid, hostName));
		spawningServiceNames.remove(createServiceName(uuid));
		decSpawn();
	}

	@Activate
	public void init() throws IOException, ApiException {
		try {
			ApiClient client = ClientBuilder.cluster().build();
			client.setDebugging(DEBUGGING);
			Configuration.setDefaultApiClient(client);

			spawnServices("init", MIN_AVAILABLE_SERVICES);

			startWatchingJobs();
			startCleanupWatch();
		} catch (ApiException e) {
			System.err.println("API Exception: " + e.getResponseBody());
			throw e;
		}
	}

	@GET
	@Produces("text/plain")
	public void startInstance(@Suspended final AsyncResponse response) throws IOException, ApiException {
		requestSpawner.execute(() -> {
			try {
				String result = doStartInstance();
				Response jaxrs = Response.ok(result).type(MediaType.TEXT_PLAIN).build();
				response.resume(jaxrs);
			} catch (InstanceLaunchException e) {
				Response.status(503).entity(e.getMessage()).build();
				Response jaxrs = Response.status(503).entity(e.getMessage()).build();
				response.resume(jaxrs);
			}
		});

		if (!PARALLEL_SPAWN) {
			// enqueue a wait between requests
			requestSpawner.execute(() -> {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
				}
			});
		}
	}

	/**
	 * Prefixes the given string with this server's {@code PREFIX} and a separator.
	 * @param toPrefix
	 * @return The prefixed string
	 */
	private static String withPrefix(String toPrefix) {
		return String.format("%s-%s", PREFIX, toPrefix);
	}

	private String doStartInstance() throws InstanceLaunchException {
		String requestUUID = UUID.randomUUID().toString();
		try {
			// TODO unit test this
			// TODO clean up evicted pods
			// TODO change props in this file to come from kubernetes configuration
			// TODO what happens when this pod get killed? how do we clean up
			// TODO shuffle exposed port

			log(requestUUID,
					MessageFormat.format("Handling the request on thread {0}", Thread.currentThread().getName()));
			log(requestUUID, MessageFormat.format("Currently there are {0} services in use. Max is {1}",
					servicesInUse.size(), MAX_CONCURRENT_USERS));

			if (servicesInUse.size() >= MAX_CONCURRENT_USERS) {
				log(requestUUID, "Too many users");
				throw new InstanceLaunchException("Too many concurrent users at the moment. Please try again later.");
			}

			/*
			 * first make sure that we have IPs available, so spawn up some service for
			 * future requests
			 */
			spawnServices(requestUUID, MIN_AVAILABLE_SERVICES + 1 /* +1 because we will take one */);

			/* pick one of the dedicated services */
			AvailableService serviceDescription = getDedicatedService(requestUUID);

			/* start up job */
			String jobName = createJob(requestUUID, serviceDescription.uuid());

			servicesInUse.put(jobName, serviceDescription);

			if (!waitForJobPod(requestUUID, jobName)) {
				throw new InstanceLaunchException(
						"There was a problem with your request, please try again. If it continues to fail, please try again later");
			}

			waitForURL(requestUUID, "http://" + serviceDescription.hostName + "");

			return "http://" + serviceDescription.hostName + "/#/coffee-editor/backend/examples/SuperBrewer3000";
		} catch (Exception e) {
			if (e instanceof InstanceLaunchException) {
				throw (InstanceLaunchException) e;
			}
			log(requestUUID, "There was an exception:");
			e.printStackTrace();

			throw new InstanceLaunchException(
					"There was a problem with your request, please try again. If it continues to fail, please try again later");
		}
	}

	private void waitForURL(String requestUUID, String url) {
		for (int i = 0; i < 150; i++) {
			try {
				final URLConnection connection = new URL(url).openConnection();
				connection.connect();

				try {
					// give backend some more time
					Thread.sleep(15000);
				} catch (InterruptedException e) {
					// silent
				}

				return;
			} catch (final MalformedURLException e) {
			} catch (final IOException e) {
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
		}
		log(requestUUID, MessageFormat.format("Waiting for url {0} timed out".toUpperCase(), url));
	}

	private boolean waitForJobPod(String requestUUID, String jobName) {
		long start = System.currentTimeMillis();
		for (int i = 0; i < 1500; i++) {
			try {
				// TODO watch
				Thread.sleep(200);
				CoreV1Api coreV1Api = new CoreV1Api();
				V1PodList podList = coreV1Api.listNamespacedPod(NAMESPACE, Boolean.TRUE, null, null, null, null, null,
						null, null, Boolean.FALSE);
				for (V1Pod pod : podList.getItems()) {
					String name = pod.getMetadata().getGenerateName();
					if (name == null) {
						continue;
					}
					if (!name.startsWith(jobName)) {
						continue;
					}
					V1PodStatus status = pod.getStatus();
					if (status == null) {
						continue;
					}
					String phase = status.getPhase();
					if ("Running".equals(phase)) {
						log(requestUUID, MessageFormat.format("Waiting for job {0} to be running took {1}ms", jobName,
								(System.currentTimeMillis() - start)));
						return true;
					}
				}
			} catch (InterruptedException e) {
			} catch (ApiException e) {
			}
		}
		log(requestUUID, MessageFormat.format("Waiting for job {0} to be running failed and took {1}ms".toUpperCase(),
				jobName, (System.currentTimeMillis() - start)));
		return false;
	}

	private void spawnServices(String requestUUID, int minAvailableServices) throws ApiException {
		log(requestUUID, MessageFormat.format("Currently we have {0} available services", availableServices.size()));
		int servicesToSpawn = incSpawn(minAvailableServices);
		log(requestUUID, MessageFormat.format("Spawning {0} additional services", servicesToSpawn));
		for (int i = 0; i < servicesToSpawn; i++) {
			String uuid = UUID.randomUUID().toString();
			long start = System.currentTimeMillis();
			String serviceName = createService(uuid);
			String hostName = createIngress(uuid, serviceName);
			addToAvailableOnceReady(uuid, serviceName, hostName);
			log(requestUUID, MessageFormat.format("Initiating spawning a service with uuid {0} took {1}ms", uuid,
					(System.currentTimeMillis() - start)));
		}
	}

	private void addToAvailableOnceReady(String uuid, String serviceName, String hostName) {
		serviceSpawner.execute(() -> {
			try {
				for (int i = 0; i < 3600; i++) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
					CoreV1Api coreV1Api = new CoreV1Api();
					V1ServiceList listNamespacedService = coreV1Api.listNamespacedService(NAMESPACE, Boolean.TRUE, null,
							null, null, null, null, null, null, Boolean.FALSE);
					for (V1Service v1Service : listNamespacedService.getItems()) {
						if (serviceName.equals(v1Service.getMetadata().getName())) {
							log("init", MessageFormat.format("Adding a new service. {0} are available",
									availableServices.size() + 1));
							addAvailableServiceAndDecSpawn(uuid, hostName);
							return;
						}
					}
				}
			} catch (ApiException e) {
				e.printStackTrace();
			}
			decSpawn();
			log("init", MessageFormat.format("Adding a new service with id {0} failed. {1} are available".toUpperCase(),
					uuid, availableServices.size()));
		});
	};

	private AvailableService getDedicatedService(String requestUUID) {
		log(requestUUID, MessageFormat.format("Currently we have {0} available services", availableServices.size()));
		long start = System.currentTimeMillis();
		AvailableService dedicatedService = availableServices.poll();
		while (dedicatedService == null) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			dedicatedService = availableServices.poll();
		}
		log(requestUUID, MessageFormat.format("Getting a dedicated service to use with id {0} took {1}ms",
				dedicatedService.uuid(), (System.currentTimeMillis() - start)));
		return dedicatedService;
	}

	private void startWatchingJobs() {
		new Thread(this::watchJobs).start();
	}

	private void startCleanupWatch() {
		new Thread(this::cleanUpWatch).start();
	}

	private void cleanUpWatch() {
		try {
			Thread.sleep(TimeUnit.SECONDS.toMillis(ACTIVE_DEADLINE_SECONDS));
		} catch (InterruptedException e) {
		}

		while (true) {
			Set<String> knownServiceUUIDs = Stream.concat(//
					Stream.concat(//
							availableServices.stream().map(AvailableService::uuid), //
							servicesInUse.values().stream().map(AvailableService::uuid))
							.map(ExampleServer.this::createServiceName), //
					spawningServiceNames.stream())

					.collect(Collectors.toSet());

			CoreV1Api coreV1Api = new CoreV1Api();
			try {
				V1ServiceList listNamespacedService = coreV1Api.listNamespacedService(NAMESPACE, Boolean.TRUE, null,
						null, null, null, null, null, null, Boolean.FALSE);
				Set<String> unknownServices = new LinkedHashSet<>();
				for (V1Service v1Service : listNamespacedService.getItems()) {
					if (knownServiceUUIDs.contains(v1Service.getMetadata().getName())) {
						continue;
					}
					unknownServices.add(v1Service.getMetadata().getName());
				}

				BatchV1Api batchV1Api = new BatchV1Api();
				V1JobList listNamespacedJob = batchV1Api.listNamespacedJob(NAMESPACE, Boolean.TRUE, null, null, null,
						null, null, null, null, Boolean.FALSE);
				for (V1Job v1Job : listNamespacedJob.getItems()) {
					String serviceName = v1Job.getMetadata().getName().replace(withPrefix("demo-job"),
							withPrefix("demo-service"));
					if (unknownServices.contains(serviceName)) {
						// job for unknown service. is this job still running?
						if (v1Job.getStatus() != null) {
							Integer activePods = v1Job.getStatus().getActive();
							if (activePods != null && activePods > 0) {
								unknownServices.remove(serviceName);
								System.out.println(serviceName + " is unknown but there is a running job");
							}
						}

					}
				}
				unknownServices.forEach(ExampleServer.this::deleteServiceAndIngressWithServiceName);
			} catch (Exception e) {
			}

			try {
				Thread.sleep(TimeUnit.HOURS.toMillis(CLEANUP_TIMEFRAME_HOURS));
			} catch (InterruptedException e) {
			}
		}
	}

	private void watchJobs() {
		try {
			while (true) {
				try {
					Thread.sleep(TimeUnit.MINUTES.toMillis(JOB_CLEANUP_TIMEFRAME_MINUTES));
				} catch (InterruptedException e) {
				}
				if (servicesInUse.isEmpty()) {
					continue;
				}

				Set<String> finishedJobs = new LinkedHashSet<>(servicesInUse.keySet());

				/* check if job associated with a service in use has finished */
				BatchV1Api batchV1Api = new BatchV1Api();
				V1JobList listNamespacedJob = batchV1Api.listNamespacedJob(NAMESPACE, Boolean.TRUE, null, null, null,
						null, null, null, null, Boolean.FALSE);
				for (V1Job v1Job : listNamespacedJob.getItems()) {
					/* filter out the running jobs */
					if (v1Job.getStatus() != null) {
						Integer activePods = v1Job.getStatus().getActive();
						if (activePods != null && activePods > 0) {
							finishedJobs.remove(v1Job.getMetadata().getName());
						}
					}
				}

				for (String job : finishedJobs) {
					AvailableService service = servicesInUse.remove(job);
					if (service != null) {
						deleteServiceAndIngress(service.uuid());
					}
					deleteJob(job);
				}

			}
		} catch (Exception e) {
		}
	}

	private void deleteJob(String job) {
		BatchV1Api batchV1Api = new BatchV1Api();
		try {
			batchV1Api.deleteNamespacedJob(job, NAMESPACE, null, null, null, null, null, null);
		} catch (ApiException e) {
		} catch (Exception e) {
		}
	}

	private void deleteServiceAndIngress(String uuid) {
		deleteServiceAndIngressWithServiceName(withPrefix("demo-service-" + uuid));
	}

	private void deleteServiceAndIngressWithServiceName(String name) {
		System.err.println("DELETING SERVICE WITH NAME " + name);
		CoreV1Api coreV1Api = new CoreV1Api();
		try {
			coreV1Api.deleteNamespacedService(name, NAMESPACE, null, null, null, null, null, null);
		} catch (ApiException e) {
		} catch (Exception e) {
		}

		String ingressName = name.replace(withPrefix("demo-service"), withPrefix("demo-ingress"));
		System.err.println("DELETING INGRESS WITH NAME " + ingressName);
		ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();
		try {
			api.deleteNamespacedIngress(ingressName, NAMESPACE, null, null, null, null, null, null);
		} catch (ApiException e) {
		} catch (Exception e) {
		}
	}

	private String createServiceName(String uuid) {
		return withPrefix("demo-service-" + uuid);
	}

	private String createIngressName(String uuid) {
		return withPrefix("demo-ingress-" + uuid);
	}

	private String createService(String uuid) throws ApiException {
		CoreV1Api coreV1Api = new CoreV1Api();
		String name = createServiceName(uuid);
		spawningServiceNames.add(name);
		V1Service service = new V1ServiceBuilder()//
				.withApiVersion("v1")//
				.withNewMetadata()//
				/**/.withName(name)//
				/**/.withLabels(Collections.singletonMap("app", withPrefix(uuid)))//
				/**/.withNamespace(NAMESPACE).endMetadata()//
				.withNewSpec()//
				/**/.withExternalTrafficPolicy("Cluster")//
				/**/.addNewPort()//
				/*    */.withPort(4000)//
				/*    */.withProtocol("TCP")//
				/*    */.withTargetPort(new IntOrString(3000)).endPort()//
				/**/.withSelector(Collections.singletonMap("app", withPrefix(uuid)))//
				/**/.withSessionAffinity("None")//
				/**/.withType("NodePort").endSpec().build();
		coreV1Api.createNamespacedService(NAMESPACE, service, null, null, null);
		return name;
	}

	private String createIngress(String uuid, String serviceName) throws ApiException {
		String hostName = uuid + "." + HOST;
		ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();
		String name = createIngressName(uuid);
		V1beta1Ingress ingress = new V1beta1IngressBuilder()//
//				.withApiVersion("networking.k8s.io/v1beta1")//
				.withNewMetadata()//
				/**/.withName(name)//
				/**/.withAnnotations(createIngressAnnotations(serviceName)).endMetadata()//
				.withNewSpec()//
				/**/.addNewRule()//
				/*    */.withHost(hostName)//
				/*    */.withNewHttp()//
				/*        */.addNewPath()//
				/*            */.withPath("/")//
				/*            */.withNewBackend()//
				/*                */.withServiceName(serviceName)
				/*                */.withServicePort(new IntOrString(4000)).endBackend().endPath().endHttp().endRule()
				.endSpec().build();
		api.createNamespacedIngress(NAMESPACE, ingress, null, null, null);
		return hostName;
	}

	private Map<String, String> createIngressAnnotations(String serviceName) {
		Map<String, String> annotations = new LinkedHashMap<>();
		annotations.put("kubernetes.io/ingress.class", "nginx");
		annotations.put("nginx.ingress.kubernetes.io/proxy-connect-timeout", "3600");
		annotations.put("nginx.ingress.kubernetes.io/proxy-read-timeout", "3600");
		annotations.put("nginx.org/websocket-services", serviceName);
		return annotations;
	}

	private String createJob(String requestUUID, String uuid) throws ApiException {
		long start = System.currentTimeMillis();
		BatchV1Api batchV1Api = new BatchV1Api();
		V1Job v1Job = new V1JobBuilder()//
				.withApiVersion("batch/v1")//
				.withNewMetadata()//
				/**/.withName(withPrefix("demo-job-" + uuid))//
				/**/.withLabels(Collections.singletonMap("app", withPrefix(uuid)))//
				/**/.withNamespace(NAMESPACE).endMetadata()//
				.withNewSpec()//
				/**/.withBackoffLimit(1)//
				/**/.withActiveDeadlineSeconds(ACTIVE_DEADLINE_SECONDS)//
				/**/.withTtlSecondsAfterFinished(60)//
				/**/.withNewTemplate()//
				/*    */.withNewMetadata()//
				/*        */.withLabels(Collections.singletonMap("app", withPrefix(uuid)))
				/*        */.withAnnotations(ingressBandwidthLimit(INGRESS_LIMIT + "M")).endMetadata()//
				/*    */.withNewSpec()//
				/*        */.withRestartPolicy("Never")//
				/*        */.withAutomountServiceAccountToken(false)//
				/*        */.addNewInitContainer()//
				/*            */.withName(withPrefix("demo-traffic-shaping"))//
				/*            */.withImage(IMAGE_REGISTRY + "tc-init:latest")//
				/*            */.withEnv(new V1EnvVar().name("EGRESS_BANDWIDTH").value(EGRESS_LIMIT + "mbit"))//
				/*            */.withNewSecurityContext()//
				/*                */.withRunAsUser(0l)//
				/*                */.withNewCapabilities()//
				/*                  */.withAdd("NET_ADMIN").endCapabilities().endSecurityContext().endInitContainer()//
				/*        */.addNewContainer()//
				/*            */.withName(withPrefix("demo"))//
				/*            */.withImage(IMAGE_REGISTRY + IMAGE_NAME + ":" + IMAGE_VERSION)//
				/*            */.withNewResources()//
				/*                */.addToRequests("memory", Quantity.fromString("1.6G"))//
				/*                */.addToLimits("memory", Quantity.fromString("1.6G"))
				/*                */.addToRequests("cpu", Quantity.fromString("0.19")).endResources()//
				/*            */.addNewPort()//
				/*                */.withContainerPort(3000).endPort().endContainer().endSpec().endTemplate().endSpec()
				/*                */.build();
		batchV1Api.createNamespacedJob(NAMESPACE, v1Job, true, "true", null);
		log(requestUUID, MessageFormat.format("Creating a job with UUID {0} took {1}ms", uuid,
				(System.currentTimeMillis() - start)));
		return withPrefix("demo-job-" + uuid);
	}

	private Map<String, String> ingressBandwidthLimit(String ingress) {
		Map<String, String> result = new LinkedHashMap<>();
		result.put("kubernetes.io/ingress-bandwidth", ingress);
//		result.put("kubernetes.io/egress-bandwidth", egress); // egress does not seems to work
		return result;
	}

	private static void log(String requestUUID, String msg) {
		System.out.println(MessageFormat.format("REQ[{0}] --- {1}", requestUUID, msg));
	}

}
