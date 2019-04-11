# Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

# SPDX-License-Identifier: EPL-2.0
# License-Filename: LICENSES/EPL-2.0.txt

FROM java:8-alpine
ADD target/subscribe-0.1.1-standalone.jar /subscribe/subscribe-0.1.1-standalone.jar
ENV SUBSCRIBE_PORT ${SUBSCRIBE_PORT}
ENV MAILGUN_MAILING_LIST ${MAILGUN_MAILING_LIST}
ENV MAILGUN_API_KEY ${MAILGUN_API_KEY}
EXPOSE ${SUBSCRIBE_PORT}
CMD ["java", "-jar", "/subscribe/subscribe-0.1.1-standalone.jar"]
