Hi! Thanks for your interest in contributing to Elasticsearch Learning to Rank. We need all kinds of help to keep this project healthy part of the Elasticsearch community.

# How you can help

We have many ways you can help with this project.

- Help with issues that are really support items / questions 
- Add new model types / features to the codebase (such as LightGBM, etc)
- Help us with upgrades to major / minor Elasticsearch updates
- Improve the docs to cover areas not well explored in plugin functionality
- Blog and share your use cases of the plugin in your codebase
- Updating this file as you encounter gotchas with development

# How to develop - the basics!

Development is driven via gradle. Specifically, development is driven by the gradle esplugin plugin (yes a plugin plugin ;) ). All gradle settings are in [build.gradle](build.gradle) file. The primary build tasks are driven by `esplugin`

## The full build process

To fully build the plugin, and get a `.zip` file, in a suitable format for an Elasticsearch plugin, run `clean` and `check` via gradle:

```
./gradlew clean check
```

The full build process does quite a bit, compiling the source code, running unit tests, integration tests, checking against Elasticsearch code style standards (watch out for those wildcard imports!), and more. If one of these steps doesn't work, you can run that gradle task by itself (via gradle)

### Using IntelliJ

We recommend using IntelliJ for development (though nothing precludes other methods). We recommend starting IntelliJ via the gradle task:

```
./gradlew idea open
```

You can clean your project files (such as switching a branch), with 

```
./gradlew idea clean
```

### Juggling Multiple Java Versions

Elastic is pretty religious about upgrading Java versions. This means if a month ago, you developed the plugin with Java 10, you might need Java 12 for the latest. You might just want to keep multiple JDK versions on your laptop, and be able to switch between them, by setting `JAVA_HOME` accordingly:

```
export JAVA11_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.1.jdk/Contents/Home/
export JAVA8_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home/
export JAVA9_HOME=/Library/Java/JavaVirtualMachines/jdk-9.0.4.jdk/Contents/Home
export JAVA10_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home/
export JAVA12_HOME=/Library/Java/JavaVirtualMachines/jdk-12.0.1.jdk/Contents/Home/ 
```

You can then set JAVA_HOME to what you need it to be:

```
export JAVA_HOME=$JAVA12_HOME
```

You can use a tool called [direnv](https://github.com/direnv/direnv) to change your JAVA_HOME automatically when within your `elasticsearch-learning-to-rank` plugin.

# Pull Requests

We of course welcome all kinds of pull requests, bug fixes, enhancements, etc. Please simply create your own fork of the repo, and send us a PR for our review. Run the full build process (`./gradlew clean check`) and confirm all tests & integration tests pass. 

Be patient, as maintainers have day jobs, and the plugin is relatively stable at this point. Which means we get around to it within a week or so. If you haven't heard from us after a week, please don't be afraid to bump, or bother us on [search relevance slack](http://o19s.com/slack).

## Elasticsearch Version-Specific Branches

Elasticsearch insists that any plugin is built against the full major.minor.patch version of the plugin. So if you have a plugin for 6.6.1 and you run 6.6.2 in production, you won't be able to install it.

As a practice, we maintain a branch per Elasticsearch version, prefixed by ES. Such as `es_6_6`. We will publish any updated builds for the Elasticsearch 6.6 version out of this branch (almost always just minor version updates). Sometimes we've been thoughtful about every minor version of 6.6, sometimes not.

### Upgrading to latest Elasticsearch major/minor version (ie `es_6_7`)

Upgrading to the latest ES version is a great way to help us out, and get your feet wet. The first step is to open `gradle.properties`, and change the ES/Lucene version numbers in the dependencies to the version you wish to upgrade to. We recommend trying to build, (it'll likely fail) making a branch name with the es version number (ie `es_6_7`), and then sending us a "Work in Progress" PR to master. This will let us rally around the (sometimes annoyingly painful) upgrade as a team.

#### Gradlew Wrapper and Java Upgrade

The first thing you'll want to do when upgrading Elasticsearch LTR is upgrading your Java version. We recommend getting the latest OpenJDK from [jdk.java.net](http://jdk.java.net). Ensure your JAVA_HOME is pointed at where you unzip the JDK contents. See "Juggling Multiple Java Versions" above. Be sure to update the travis.yml to this JDK version.

The gradlew / gradlew.bat also usually need to be ugpraded to latest. You'll get an error when you run `./gradlew clean check` telling you what gradle to upgrade to. Unfortunately there's a catch 22: you can't run the gradle upgrade task precisely because of the gradle error that Elastic's build system outputs. So here's what I do

```
mv build.gradle /tmp/ # Get the Elastic LTR gradle file out of the way, dont worry, you can use git to get it back!
./gradlew stop # Stop any existing gradle daemons
./gradlew wrapper --gradle-version 6.4 #Upgrade (here to 6.4)
git checkout build.gradle # Restore build.gradle
```


#### Elasticsearch Code Spelunking!

A lot of the upgrade will involve understanding how Elasticsearch's code has changed (such as from `6.6` to `6.7`)  that impacts our code base. For example, our code may inherit from some Elasticsearch base class that's changed/been refactored between versions. You'll spend a lot of your time in the [Elasticsearch repo](http://github.com/elastic/elasticsearch), going to the file (like a base class) that changed, finding the commit that caused the change, and then seeing how other, similar parts of the Elastic code base (like other plugins), responded to the change. Then we attempt to make that change to the relevant pieces of our code.

## Backporting Bug Fixes / Features to Older Elastic Plugin Versions

The general rule is master tracks to the last ES version upgrade we did. And master also has the latest & greatest ES plugin features/bug fixes.

But, you say, you're a happy user of the plugin on version 6.6. That's what's in production, and you aren't exactly going to upgrade to the latest Elasticsearch just to get the features / bug fixes added since then for this humble plugin. What should you do?

Should you send in a PR to that ES version? Well from the maintainers point of view, this gets tricky to track. If 6.6 has feature X but 6.7 doesn't, but instead has feature Y, how do we track & understand which ES plugin versions have which features? What 'plugin version' should it be? If you are in a position where you need to backport a lot of features, get comfortable building and deploying the plugin yourself for your own production needs.

We WILL often accept simple bug fixes on a relatively conservative basis ('obviously broken' stuff).

# Helping With The Docs!

Documentation is the undersung hero of every open source project! People who write docs, especially those on contributing to open source projects, should be showered in cash and prizes.

## Docs development

[Docs](/docs) are built using Sphinx and written in reStructuredText. After [installing sphinx](https://www.sphinx-doc.org/en/master/index.html) (`pip install sphinx`) rebuild the docs with:

```
cd docs
make html
```

In another tab, you can simply run your favorite HTTP server:

```
python -m http.server
```

Visit [localhost:8000](http://localhost:8000) and browse to the `_build/html` directory in your browser to view the built docs. 

Docs changes at master will be automatically built and deployed to readthedocs.

## Docs Content / Editorial Guidelines

Most people come to this plugin, unfamiliar with the basic Learning to Rank workflow. We want this documentation to read as a walkthrough of the Learning to Rank workflow, without getting into the 'advanced' stuff yet. So save the caveats and wherefores to the later section (ie "Advanced Functionality") and keep the 80% 'what you need to know' to the chapters working to tell a story on how to do Learning to Rank.

# Other questions? Get in touch!

Please open an issue to ask any questions about contributing not covered by this document. If there's a "bug" in this document, please feel free to file PR, typos are the wosrt.
