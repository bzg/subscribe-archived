# Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
# SPDX-License-Identifier: EPL-2.0
# License-Filename: LICENSES/EPL-2.0.txt

FROM java:8-alpine
ENV SUBSCRIBE_CONFIG ${SUBSCRIBE_CONFIG}
ADD target/subscribe-0.2.0-standalone.jar /subscribe/subscribe-0.2.0-standalone.jar
CMD ["java", "-jar", "/subscribe/subscribe-0.2.0-standalone.jar"]
