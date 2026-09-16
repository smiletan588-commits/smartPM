export function canCommentInProject(member, isOwner = false) {
  return Boolean(isOwner || (member && member.permission !== 'VIEWER'))
}

export function projectCapabilities(project) {
  const owner = Boolean(project?.owner)
  const permission = project?.myPermission || (owner ? 'PROJECT_ADMIN' : 'MEMBER')
  return {
    owner,
    permission,
    canWrite: owner || permission !== 'VIEWER',
    canManageProject: owner || permission === 'PROJECT_ADMIN',
    canManageMembers: owner || permission === 'PROJECT_ADMIN',
    canDeleteProject: owner
  }
}

export function permissionReason(capability) {
  return ({
    write: '只读成员只能查看项目内容',
    manageProject: '只有项目负责人或项目管理员可以修改',
    manageMembers: '只有项目负责人或项目管理员可以管理成员',
    deleteProject: '仅项目负责人可删除项目'
  })[capability] || '当前账号没有执行此操作的权限'
}

export function riskLevelLabel(level) {
  return ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' })[level] || level
}
