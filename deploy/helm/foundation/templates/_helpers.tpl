{{/* 公共命名与标签（F-11 §3.2：全部用 app.kubernetes.io/* 标签） */}}
{{- define "foundation.name" -}}
foundation
{{- end -}}

{{- define "foundation.labels" -}}
app.kubernetes.io/name: {{ include "foundation.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "foundation.secretName" -}}
{{- if .Values.existingSecret -}}
{{ .Values.existingSecret }}
{{- else -}}
foundation-secret
{{- end -}}
{{- end -}}
