{{/*
Create Resource Group Controller app version
*/}}
{{- define "resource-group-controller.version" -}}
{{- default .Chart.AppVersion .Values.image.tag }}
{{- end -}}

{{/*
Resource Group Controller Envs Configuration
*/}}
{{- define "resource-group-controller.cm.envs" -}}
TZ: {{ .Values.app.timezone }}
SPRING_PROFILES_ACTIVE: {{ .Values.app.profile }}
LOGGING_LEVEL_IO_TEN1010_COASTER_GROUPCONTROLLER: {{ .Values.app.loggingLevel }}
SERVER_SSL_CERTIFICATE: {{ .Values.app.certificatePath }}/tls.crt
SERVER_SSL_CERTIFICATE_PRIVATE_KEY: {{ .Values.app.certificatePath }}/tls.key
SERVER_SSL_TRUST_CERTIFICATE: {{ .Values.app.certificatePath }}/ca.crt
APP_SCHEDULING_GROUP_NODE_ONLY: '{{ .Values.app.schedulingGroupNodeOnly }}'
{{- end }}