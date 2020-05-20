# Cloud-native Patterns

This repository contains patterns that can be used to design complex,
cloud-native applications. The paper [A Cloud Native Platform for Stateful Streaming](https://arxiv.org/abs/2006.00064)
contains an in-depth description of these patterns.

## Patterns

### Controller

Kubernetes defines controllers as "control loops that tracks at least one
resource type". We constrain that definition further: in cloud native Streams,
a controller is a control loop that tracks a single resource type. Controllers
take some action on creation, modification and deletion of a resource type. As
with regular resources, custom resources can be monitored using controllers. 

### Conductor

In contrast to controllers, the conductor pattern observes events from multiple
resources and does not save state updates in a local cache. Instead, they are
concurrent control loops that maintain a state machine that transitions based on
resource events, all towards a final goal. Conductors do not own any resources.
Rather, they register themselves with existing controllers as generic event
listeners which receive the same notifications that each controller does.

### Coordinator

When asynchronous agents need to modify the same resource, we use the
coordinator pattern. The coordinator pattern implements a multiple-reader,
single-writer access model by granting ownership of the resource to a single
agent and serializing asynchronous modification requests coming from other
agents. Coordinators are synchronous command queues that serially execute
modification commands on resources. In cloud native Streams, this pattern means
that the controller for a resource owns that resource, and other controllers
which want to modify it must make requests to that controller.

## Getting started

The repository contains a custom `Job` resource example that makes use of those
patterns. It is located in the `com.ibm.cnp.samples` package.
 
### Compiling the code
 
To compile the code, simply import the repository as a `Maven` project in your
favorite IDE, or run the following command at the root of the repository:
```bash
$ mvn package
```

### Running the example

You need first to install the `Job` custom resource definition into your
Kubernetes cluster:
```bash
$ kubectl apply -f ${REPO_ROOT}/crds/job.yaml
```
Then, run the `com.ibm.cnp.samples.Main` class either through your IDE or
by typing the following command at the root of the repository:
```bash
$ mvn exec:java
```
