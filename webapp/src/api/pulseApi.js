import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import keycloak from '../keycloak'

const rawBaseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api',
  prepareHeaders: async (headers) => {
    // refresh if the token expires within 30s, then attach it
    try {
      await keycloak.updateToken(30)
    } catch {
      keycloak.login()
    }
    if (keycloak.token) headers.set('Authorization', `Bearer ${keycloak.token}`)
    return headers
  }
})

export const pulseApi = createApi({
  reducerPath: 'pulseApi',
  baseQuery: rawBaseQuery,
  tagTypes: ['Users'],
  endpoints: (builder) => ({
    getGroups: builder.query({
      query: () => '/groups',
      keepUnusedDataFor: 0
    }),
    createGroup: builder.mutation({
      query: (body) => ({ url: '/groups', method: 'POST', body })
    }),
    getTagRules: builder.query({
      query: () => '/tag-rules',
      keepUnusedDataFor: 0
    }),
    createTagRule: builder.mutation({
      query: (body) => ({ url: '/tag-rules', method: 'POST', body })
    }),
    evaluateTagRules: builder.mutation({
      query: () => ({ url: '/tag-rules/evaluate', method: 'POST' })
    }),
    createEnrolmentToken: builder.mutation({
      query: (body) => ({ url: '/enrolment/tokens', method: 'POST', body })
    }),
    getEndpoints: builder.query({
      query: () => '/endpoints',
      keepUnusedDataFor: 0
    }),
    updateEndpointGroup: builder.mutation({
      query: ({ id, groupId }) => ({
        url: `/endpoints/${id}/group`,
        method: 'PUT',
        body: { groupId }
      })
    }),
    updateEndpointTags: builder.mutation({
      query: ({ id, tags }) => ({
        url: `/endpoints/${id}/tags`,
        method: 'PUT',
        body: { tags }
      })
    }),
    getScripts: builder.query({
      query: ({ status = 'all', page = 0, size = 50 } = {}) =>
        `/scripts?status=${status}&page=${page}&size=${size}`,
      keepUnusedDataFor: 0
    }),
    getScript: builder.query({
      query: (id) => `/scripts/${id}`,
      keepUnusedDataFor: 0
    }),
    createScript: builder.mutation({
      query: (body) => ({ url: '/scripts', method: 'POST', body })
    }),
    approveScript: builder.mutation({
      query: (id) => ({ url: `/scripts/${id}/approve`, method: 'POST' })
    }),
    runScript: builder.mutation({
      query: ({ id, ...body }) => ({ url: `/scripts/${id}/run`, method: 'POST', body })
    }),
    getScriptRunResults: builder.query({
      query: (runId) => `/scripts/runs/${runId}/results`,
      keepUnusedDataFor: 0
    }),
    ackScriptExecution: builder.mutation({
      query: ({ runId, endpointId, ...body }) => ({
        url: `/scripts/runs/${runId}/endpoints/${endpointId}/ack`,
        method: 'POST',
        body
      })
    }),
    getSoftware: builder.query({
      query: (endpointId) => `/endpoints/${endpointId}/software`,
      keepUnusedDataFor: 0
    }),
    installSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/install`,
        method: 'POST',
        body
      })
    }),
    updateSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/update`,
        method: 'POST',
        body
      })
    }),
    removeSoftware: builder.mutation({
      query: ({ endpointId, ...body }) => ({
        url: `/endpoints/${endpointId}/software/remove`,
        method: 'POST',
        body
      })
    }),
    getEndpoint: builder.query({
      query: (id) => `/endpoints/${id}`,
      keepUnusedDataFor: 0
    }),
    getMetrics: builder.query({
      query: ({ id, from, to, type, labels }) => {
        const params = new URLSearchParams({ from, to, type })
        if (labels) {
          for (const [k, v] of Object.entries(labels)) {
            params.set(`label.${k}`, v)
          }
        }
        return `/endpoints/${id}/metrics?${params}`
      },
      keepUnusedDataFor: 0
    }),
    getSystemInfo: builder.query({
      query: (id) => `/endpoints/${id}/system-info`,
      keepUnusedDataFor: 0
    }),
    listFiles: builder.query({
      query: ({ id, path }) =>
        `/files/${id}${path ? `?path=${encodeURIComponent(path)}` : ''}`,
      keepUnusedDataFor: 0
    }),
    uploadFile: builder.mutation({
      query: ({ id, path, file }) => {
        const form = new FormData()
        form.append('file', file)
        return {
          url: `/files/${id}/upload?path=${encodeURIComponent(path)}`,
          method: 'POST',
          body: form
        }
      }
    }),
    createSession: builder.mutation({
      query: (body) => ({ url: '/sessions', method: 'POST', body })
    }),
    getSession: builder.query({
      query: (id) => `/sessions/${id}`,
      keepUnusedDataFor: 0
    }),
    endSession: builder.mutation({
      query: (id) => ({ url: `/sessions/${id}`, method: 'DELETE' })
    }),
    getAlertRules: builder.query({
      query: () => '/alert-rules',
      keepUnusedDataFor: 0
    }),
    createAlertRule: builder.mutation({
      query: (body) => ({ url: '/alert-rules', method: 'POST', body })
    }),
    deleteAlertRule: builder.mutation({
      query: (id) => ({ url: `/alert-rules/${id}`, method: 'DELETE' })
    }),
    getAlerts: builder.query({
      query: (status = 'open') => `/alerts?status=${status}`,
      keepUnusedDataFor: 0
    }),
    ackAlert: builder.mutation({
      query: (id) => ({ url: `/alerts/${id}/ack`, method: 'POST' })
    }),
    getAuditLog: builder.query({
      query: ({ user, endpoint, from, to, page = 0, size = 50 } = {}) => {
        const params = new URLSearchParams()
        if (user) params.set('user', user)
        if (endpoint) params.set('endpoint', endpoint)
        if (from) params.set('from', from)
        if (to) params.set('to', to)
        params.set('page', page)
        params.set('size', size)
        return `/audit?${params}`
      },
      keepUnusedDataFor: 0
    }),
    listAgentVersions: builder.query({
      query: () => '/agent-versions',
      keepUnusedDataFor: 0
    }),
    publishAgentVersion: builder.mutation({
      query: (formData) => ({ url: '/agent-versions', method: 'POST', body: formData })
    }),
    setCurrentAgentVersion: builder.mutation({
      query: (id) => ({ url: `/agent-versions/${id}/current`, method: 'PUT' })
    }),
    deleteAgentVersion: builder.mutation({
      query: (id) => ({ url: `/agent-versions/${id}`, method: 'DELETE' })
    }),
    listWebhooks: builder.query({
      query: () => '/webhooks',
      keepUnusedDataFor: 0
    }),
    createWebhook: builder.mutation({
      query: (body) => ({ url: '/webhooks', method: 'POST', body })
    }),
    updateWebhook: builder.mutation({
      query: ({ id, ...body }) => ({ url: `/webhooks/${id}`, method: 'PUT', body })
    }),
    deleteWebhook: builder.mutation({
      query: (id) => ({ url: `/webhooks/${id}`, method: 'DELETE' })
    }),
    listDeliveries: builder.query({
      query: ({ webhookId, status, limit = 50 }) => {
        const params = new URLSearchParams({ limit })
        if (status) params.set('status', status)
        return `/webhooks/${webhookId}/deliveries?${params}`
      },
      keepUnusedDataFor: 0
    }),
    getDelivery: builder.query({
      query: (deliveryId) => `/webhooks/deliveries/${deliveryId}`,
      keepUnusedDataFor: 0
    }),
    listDeadLetter: builder.query({
      query: (limit = 100) => `/webhooks/deliveries/dead-letter?limit=${limit}`,
      keepUnusedDataFor: 0
    }),
    refreshProcesses: builder.mutation({
      query: (endpointId) => ({
        url: `/endpoints/${endpointId}/processes/refresh`,
        method: 'POST'
      })
    }),
    getLatestProcesses: builder.query({
      query: (endpointId) => `/endpoints/${endpointId}/processes/latest`,
      keepUnusedDataFor: 0
    }),
    killProcess: builder.mutation({
      query: ({ endpointId, pid }) => ({
        url: `/endpoints/${endpointId}/processes/${pid}/kill`,
        method: 'POST'
      })
    }),
    getRoles: builder.query({
      query: () => '/identity/rbac/roles',
      keepUnusedDataFor: 0
    }),
    getUsers: builder.query({
      query: () => '/identity/users',
      providesTags: ['Users'],
      keepUnusedDataFor: 0
    }),
    getUser: builder.query({
      query: (id) => `/identity/users/${id}`,
      providesTags: (result, error, id) => [{ type: 'Users', id }],
      keepUnusedDataFor: 0
    }),
    createUser: builder.mutation({
      query: (body) => ({ url: '/identity/users', method: 'POST', body }),
      invalidatesTags: ['Users']
    }),
    updateUser: builder.mutation({
      query: ({ id, ...body }) => ({ url: `/identity/users/${id}`, method: 'PUT', body }),
      invalidatesTags: ['Users']
    }),
    deleteUser: builder.mutation({
      query: (id) => ({ url: `/identity/users/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Users']
    }),
    updateUserRoles: builder.mutation({
      query: ({ id, roleIds }) => ({ url: `/identity/users/${id}/roles`, method: 'PUT', body: { roleIds } }),
      invalidatesTags: ['Users']
    }),
    getOrganizations: builder.query({
      query: () => '/organizations',
      providesTags: ['Organizations'],
      keepUnusedDataFor: 0
    }),
    createOrganization: builder.mutation({
      query: (body) => ({ url: '/organizations', method: 'POST', body }),
      invalidatesTags: ['Organizations']
    }),
    deleteOrganization: builder.mutation({
      query: (id) => ({ url: `/organizations/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Organizations']
    }),
    getOrgUsers: builder.query({
      query: (orgId) => `/organizations/${orgId}/users`,
      providesTags: (result, error, orgId) => [{ type: 'OrgUsers', id: orgId }],
      keepUnusedDataFor: 0
    }),
    createOrgUser: builder.mutation({
      query: ({ orgId, ...body }) => ({ url: `/organizations/${orgId}/users`, method: 'POST', body }),
      invalidatesTags: (result, error, { orgId }) => [{ type: 'OrgUsers', id: orgId }]
    })
  })
})

export const {
  useGetGroupsQuery,
  useCreateGroupMutation,
  useGetTagRulesQuery,
  useCreateTagRuleMutation,
  useEvaluateTagRulesMutation,
  useCreateEnrolmentTokenMutation,
  useGetEndpointsQuery,
  useUpdateEndpointGroupMutation,
  useUpdateEndpointTagsMutation,
  useGetScriptsQuery,
  useGetScriptQuery,
  useCreateScriptMutation,
  useApproveScriptMutation,
  useRunScriptMutation,
  useGetScriptRunResultsQuery,
  useAckScriptExecutionMutation,
  useGetSoftwareQuery,
  useInstallSoftwareMutation,
  useUpdateSoftwareMutation,
  useRemoveSoftwareMutation,
  useGetEndpointQuery,
  useGetMetricsQuery,
  useGetSystemInfoQuery,
  useListFilesQuery,
  useUploadFileMutation,
  useCreateSessionMutation,
  useGetSessionQuery,
  useEndSessionMutation,
  useGetAlertRulesQuery,
  useCreateAlertRuleMutation,
  useDeleteAlertRuleMutation,
  useGetAlertsQuery,
  useAckAlertMutation,
  useGetAuditLogQuery,
  useListAgentVersionsQuery,
  usePublishAgentVersionMutation,
  useSetCurrentAgentVersionMutation,
  useDeleteAgentVersionMutation,
  useListWebhooksQuery,
  useCreateWebhookMutation,
  useUpdateWebhookMutation,
  useDeleteWebhookMutation,
  useListDeliveriesQuery,
  useGetDeliveryQuery,
  useListDeadLetterQuery,
  useRefreshProcessesMutation,
  useGetLatestProcessesQuery,
  useKillProcessMutation,
  useGetRolesQuery,
  useGetUsersQuery,
  useGetUserQuery,
  useCreateUserMutation,
  useUpdateUserMutation,
  useDeleteUserMutation,
  useUpdateUserRolesMutation,
  useGetOrganizationsQuery,
  useCreateOrganizationMutation,
  useDeleteOrganizationMutation,
  useGetOrgUsersQuery,
  useCreateOrgUserMutation
} = pulseApi
