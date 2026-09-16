import { describe, expect, it } from 'vitest'
import { canCommentInProject, permissionReason, projectCapabilities, riskLevelLabel } from './permissions'

describe('graduation project UI rules', () => {
  it('keeps viewers read-only while allowing members and owners to comment', () => {
    expect(canCommentInProject({ permission: 'VIEWER' })).toBe(false)
    expect(canCommentInProject({ permission: 'MEMBER' })).toBe(true)
    expect(canCommentInProject(null, true)).toBe(true)
  })

  it('renders deterministic risk labels', () => {
    expect(riskLevelLabel('HIGH')).toBe('高风险')
    expect(riskLevelLabel('MEDIUM')).toBe('中风险')
    expect(riskLevelLabel('LOW')).toBe('低风险')
  })

  it('distinguishes project owners from project administrators for deletion', () => {
    expect(projectCapabilities({ owner: true, myPermission: 'PROJECT_ADMIN' }).canDeleteProject).toBe(true)
    expect(projectCapabilities({ owner: false, myPermission: 'PROJECT_ADMIN' }).canManageProject).toBe(true)
    expect(projectCapabilities({ owner: false, myPermission: 'PROJECT_ADMIN' }).canDeleteProject).toBe(false)
    expect(projectCapabilities({ owner: false, myPermission: 'VIEWER' }).canWrite).toBe(false)
    expect(permissionReason('deleteProject')).toBe('仅项目负责人可删除项目')
  })
})
