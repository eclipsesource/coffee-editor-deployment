# coffee-editor-deployment

Webpage and Rest-Service for launching the coffee-editor on GKE

## Deploying a new Coffee Editor Image

Setup kubectl to point to our cluster.

Check out https://github.com/eclipsesource/coffee-editor, update the version accordingly and create a PR for the version update.

Build the image `coffee-editor/dockerfiles/build.sh`

Test the image locally by running `docker run -p 0.0.0.0:3000:3000 --rm coffee-editor`

Retag the image (adjust verson) `docker tag coffee-editor:latest eu.gcr.io/kubernetes-238012/coffee-editor:version`

Push the image (adjust version) `docker push eu.gcr.io/kubernetes-238012/coffee-editor:version`

Run `kubectl edit configmap coffee-config -n coffee` and update `COFFEE_EDITOR` with the pushed tag.

Rescale the deployment:
`kubectl scale --replicas=0 deployment/coffee-editor-deployment -n coffee`
`kubectl scale --replicas=1 deployment/coffee-editor-deployment -n coffee`

Test the deployment (first start is slower than usual, because the new image has to be pulled from the registry)

## Deploy a new version of the REST Service

Setup kubectl to point to our cluster.

Build the rest service `mvn clean verify -f java/pom.xml`

Build the docker image `docker build --tag=coffee-editor-example .`

Retag image `docker tag coffee-editor-example eu.gcr.io/kubernetes-238012/coffee-editor-example`

Push the image `docker push eu.gcr.io/kubernetes-238012/coffee-editor-example`

Rescale the deployment:
`kubectl scale --replicas=0 deployment/coffee-editor-deployment -n coffee`
`kubectl scale --replicas=1 deployment/coffee-editor-deployment -n coffee`

Test the deployment

## Deploy a new version of the Launcher Web Page

Setup kubectl to point to our cluster.

Build docker image `cd coffee-editor-launcher-vue && docker build -t coffee-editor-vue . && cd ..`

Retag the image `docker tag coffee-editor-vue:latest eu.gcr.io/kubernetes-238012/coffee-editor-vue`

Push the image `docker push eu.gcr.io/kubernetes-238012/coffee-editor-vue`

Rescale the web page deployment:
`kubectl scale --replicas=0 deployment/coffee-editor-vue -n coffee`
`kubectl scale --replicas=1 deployment/coffee-editor-vue -n coffee`

## Initial steps (Only required when starting in a new cluster)

Setup kubectl to point to our cluster.

Service accounts used by the REST service to start new pods in the cluster `kubectl create serviceaccount api-service-account`

Create the namespaces:
`kubectl create -f namespaces/coffee.json`
`kubectl create -f namespaces/instance.json`

Create the initial config for the REST service `kubectl create configmap coffee-config --from-env-file=config/coffee-env-file.properties -n coffee`

Create initial deployments `kubectl apply -f k8s/`

# License

This program and the accompanying materials are made available under the terms of the Eclipse Public License v. 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License v. 2.0 are satisfied: MIT.

SPDX-License-Identifier: EPL-2.0 OR MIT

