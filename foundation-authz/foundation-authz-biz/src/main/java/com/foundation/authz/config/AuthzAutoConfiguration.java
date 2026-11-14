package com.foundation.authz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppManifestVersionApi;
import com.foundation.authz.api.DataDimensionApi;
import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.DirectoryApi;
import com.foundation.authz.api.DirectoryController;
import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.api.FeatureToggleController;
import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.ManifestApi;
import com.foundation.authz.api.MenuApi;
import com.foundation.authz.api.OrgAdminScopeApi;
import com.foundation.authz.api.OrgAdminScopeController;
import com.foundation.authz.api.PermissionApi;
import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.SelfManagedImportApi;
import com.foundation.authz.api.SnapshotApi;
import com.foundation.authz.api.SubsystemApi;
import com.foundation.authz.api.SubsystemController;
import com.foundation.authz.application.AppIdResolver;
import com.foundation.authz.application.AuthorizationQueryService;
import com.foundation.authz.application.DataDimensionAppService;
import com.foundation.authz.application.DataScopeAppService;
import com.foundation.authz.application.DimensionValueSyncService;
import com.foundation.authz.application.EffectiveRoleResolver;
import com.foundation.authz.application.FeatureToggleGate;
import com.foundation.authz.application.FeatureToggleService;
import com.foundation.authz.application.ManifestDiffCalculator;
import com.foundation.authz.application.ManifestImporter;
import com.foundation.authz.application.MenuService;
import com.foundation.authz.application.OrgAdminScopeService;
import com.foundation.authz.application.PermissionCodeCatalogService;
import com.foundation.authz.application.RoleGrantService;
import com.foundation.authz.application.RoleMutexChecker;
import com.foundation.authz.application.RoleService;
import com.foundation.authz.application.SelfManagedImporter;
import com.foundation.authz.application.SnapshotAppService;
import com.foundation.authz.application.SnapshotAssembler;
import com.foundation.authz.application.SnapshotRecomputeService;
import com.foundation.authz.application.SubsystemDirectoryService;
import com.foundation.authz.domain.AffectedPersonResolver;
import com.foundation.authz.domain.DataScopeResolver;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.infrastructure.AppManifestRepository;
import com.foundation.authz.infrastructure.AuthzChangedEventPublisher;
import com.foundation.authz.infrastructure.DataDimensionRepository;
import com.foundation.authz.infrastructure.DimensionValueRepository;
import com.foundation.authz.infrastructure.FeatureToggleRepository;
import com.foundation.authz.infrastructure.FunctionRepository;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.authz.infrastructure.ModuleRepository;
import com.foundation.authz.infrastructure.MybatisAppManifestRepository;
import com.foundation.authz.infrastructure.MybatisDataDimensionRepository;
import com.foundation.authz.infrastructure.MybatisDimensionValueRepository;
import com.foundation.authz.infrastructure.MybatisFeatureToggleRepository;
import com.foundation.authz.infrastructure.MybatisFunctionRepository;
import com.foundation.authz.infrastructure.MybatisMenuRepository;
import com.foundation.authz.infrastructure.MybatisModuleRepository;
import com.foundation.authz.infrastructure.MybatisOrgAdminScopeRepository;
import com.foundation.authz.infrastructure.MybatisPermissionCodeRepository;
import com.foundation.authz.infrastructure.MybatisPersonDimensionRepository;
import com.foundation.authz.infrastructure.MybatisRoleDataScopeRepository;
import com.foundation.authz.infrastructure.MybatisRoleDimensionValueRepository;
import com.foundation.authz.infrastructure.MybatisRoleGrantRepository;
import com.foundation.authz.infrastructure.MybatisRoleMutexRepository;
import com.foundation.authz.infrastructure.MybatisRoleRepository;
import com.foundation.authz.infrastructure.MybatisSelfManagedImportRepository;
import com.foundation.authz.infrastructure.MybatisSnapshotRepository;
import com.foundation.authz.infrastructure.MybatisSubsystemRepository;
import com.foundation.authz.infrastructure.NoopSubjectMembership;
import com.foundation.authz.infrastructure.OrgAdminScopeRepository;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.authz.infrastructure.PersonDimensionRepository;
import com.foundation.authz.infrastructure.RoleDataScopeRepository;
import com.foundation.authz.infrastructure.RoleDimensionValueRepository;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleMutexRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.authz.infrastructure.SelfManagedImportRepository;
import com.foundation.authz.infrastructure.SnapshotRepository;
import com.foundation.authz.infrastructure.SubsystemRepository;
import com.foundation.authz.infrastructure.mapper.AppManifestMapper;
import com.foundation.authz.infrastructure.mapper.AuthzSnapshotMapper;
import com.foundation.authz.infrastructure.mapper.DataDimensionMapper;
import com.foundation.authz.infrastructure.mapper.DimensionValueMapper;
import com.foundation.authz.infrastructure.mapper.FeatureToggleMapper;
import com.foundation.authz.infrastructure.mapper.FunctionMapper;
import com.foundation.authz.infrastructure.mapper.MenuMapper;
import com.foundation.authz.infrastructure.mapper.ModuleMapper;
import com.foundation.authz.infrastructure.mapper.OrgAdminScopeMapper;
import com.foundation.authz.infrastructure.mapper.PermissionCodeMapper;
import com.foundation.authz.infrastructure.mapper.PersonDimensionMapper;
import com.foundation.authz.infrastructure.mapper.RoleDataScopeMapper;
import com.foundation.authz.infrastructure.mapper.RoleDimensionValueMapper;
import com.foundation.authz.infrastructure.mapper.RoleGrantMapper;
import com.foundation.authz.infrastructure.mapper.RoleMapper;
import com.foundation.authz.infrastructure.mapper.RoleMutexMapper;
import com.foundation.authz.infrastructure.mapper.RolePermissionMapper;
import com.foundation.authz.infrastructure.mapper.SelfManagedImportItemMapper;
import com.foundation.authz.infrastructure.mapper.SelfManagedImportMapper;
import com.foundation.authz.infrastructure.mapper.SubsystemMapper;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 权限中心自动装配（F-05 BAS-05）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 应用服务 → {@code -api} 契约 Bean。
 *
 * <p>依赖 {@link SqlSessionFactory} 与 {@link AppApi}/{@link AppManifestVersionApi}
 * （应用编码解析与清单版本读写）；缺失时整块跳过，纯配置上下文不受影响。
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration",
        "com.foundation.app.config.FoundationAppAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, AppApi.class, AppManifestVersionApi.class})
@EnableConfigurationProperties({AuthzProperties.class, SubsystemProperties.class})
public class AuthzAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AppManifestRepository appManifestRepository(AppManifestMapper mapper) {
        return new MybatisAppManifestRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MenuRepository menuRepository(MenuMapper mapper) {
        return new MybatisMenuRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionCodeRepository permissionCodeRepository(PermissionCodeMapper mapper) {
        return new MybatisPermissionCodeRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleRepository roleRepository(RoleMapper mapper, RolePermissionMapper rolePermissionMapper) {
        return new MybatisRoleRepository(mapper, rolePermissionMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleMutexRepository roleMutexRepository(RoleMutexMapper mapper) {
        return new MybatisRoleMutexRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleGrantRepository roleGrantRepository(RoleGrantMapper mapper) {
        return new MybatisRoleGrantRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataDimensionRepository dataDimensionRepository(DataDimensionMapper mapper) {
        return new MybatisDataDimensionRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DimensionValueRepository dimensionValueRepository(DimensionValueMapper mapper) {
        return new MybatisDimensionValueRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleDataScopeRepository roleDataScopeRepository(RoleDataScopeMapper mapper) {
        return new MybatisRoleDataScopeRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleDimensionValueRepository roleDimensionValueRepository(RoleDimensionValueMapper mapper) {
        return new MybatisRoleDimensionValueRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonDimensionRepository personDimensionRepository(PersonDimensionMapper mapper) {
        return new MybatisPersonDimensionRepository(mapper);
    }

    /** 组织归属端口：BAS-06 提供实现前用占位实现（组织单元与岗位来源为空）。 */
    @Bean
    @ConditionalOnMissingBean
    public SubjectMembership subjectMembership() {
        return new NoopSubjectMembership();
    }

    @Bean
    @ConditionalOnMissingBean
    public AppIdResolver appIdResolver(AppApi apps) {
        return new AppIdResolver(apps);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleMutexChecker roleMutexChecker() {
        return new RoleMutexChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public ManifestApi manifestApi(AppManifestVersionApi versions, AppIdResolver appIds,
                                   AppManifestRepository manifests, MenuRepository menus,
                                   PermissionCodeRepository permissionCodes, OutboxAppender outbox,
                                   EventProperties eventProperties) {
        return new ManifestImporter(versions, appIds, manifests, menus, permissionCodes, outbox,
                eventProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MenuApi menuApi(MenuRepository menus, AppIdResolver appIds) {
        return new MenuService(menus, appIds);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionApi permissionApi(PermissionCodeRepository permissionCodes, AppIdResolver appIds) {
        return new PermissionCodeCatalogService(permissionCodes, appIds);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleApi roleApi(RoleRepository roles, RoleGrantRepository grants, AppIdResolver appIds) {
        return new RoleService(roles, grants, appIds);
    }

    @Bean
    @ConditionalOnMissingBean
    public EffectiveRoleResolver effectiveRoleResolver(RoleGrantRepository grants, RoleMutexRepository mutexes,
                                                       RoleRepository roles, SubjectMembership membership,
                                                       AppIdResolver appIds, RoleMutexChecker mutexChecker) {
        return new EffectiveRoleResolver(grants, mutexes, roles, membership, appIds, mutexChecker);
    }

    @Bean
    @ConditionalOnMissingBean
    public GrantApi grantApi(RoleGrantRepository grants, RoleMutexRepository mutexes, RoleRepository roles,
                             EffectiveRoleResolver resolver, RoleMutexChecker mutexChecker) {
        return new RoleGrantService(grants, mutexes, roles, resolver, mutexChecker);
    }

    @Bean
    @ConditionalOnMissingBean
    public ManifestDiffCalculator manifestDiffCalculator() {
        return new ManifestDiffCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationQueryService authorizationQueryService(EffectiveRoleResolver resolver,
                                                               RoleRepository roles,
                                                               PermissionCodeRepository permissionCodes,
                                                               AppIdResolver appIds,
                                                               FeatureToggleGate toggles) {
        return new AuthorizationQueryService(resolver, roles, permissionCodes, appIds, toggles);
    }

    @Bean
    @ConditionalOnMissingBean
    public DimensionValueSyncService dimensionValueSyncService(DimensionValueRepository values) {
        return new DimensionValueSyncService(values);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeResolver dataScopeResolver() {
        return new DataScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataDimensionApi dataDimensionApi(DataDimensionRepository dimensions,
                                             DimensionValueRepository values,
                                             DimensionValueSyncService syncService) {
        return new DataDimensionAppService(dimensions, values, syncService);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeApi dataScopeApi(RoleRepository roles, RoleDataScopeRepository roleScopes,
                                     RoleDimensionValueRepository roleDimensionValues,
                                     PersonDimensionRepository personDimensions,
                                     DataDimensionRepository dimensions,
                                     EffectiveRoleResolver effectiveRoles, SubjectMembership membership,
                                     DataScopeResolver resolver, AppApi apps) {
        return new DataScopeAppService(roles, roleScopes, roleDimensionValues, personDimensions,
                dimensions, effectiveRoles, membership, resolver, apps);
    }

    // ---------- BAS-07 授权快照 ----------

    @Bean
    @ConditionalOnMissingBean
    public SnapshotRepository snapshotRepository(AuthzSnapshotMapper mapper, ObjectMapper objectMapper) {
        return new MybatisSnapshotRepository(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzChangedEventPublisher authzChangedEventPublisher(OutboxAppender outbox,
                                                                 EventProperties eventProperties) {
        return new AuthzChangedEventPublisher(outbox, eventProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AffectedPersonResolver affectedPersonResolver(SubjectMembership membership) {
        return new AffectedPersonResolver(membership);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotAssembler snapshotAssembler(EffectiveRoleResolver effectiveRoles,
                                               AuthorizationQueryService authorizations,
                                               MenuRepository menus, DataScopeApi dataScopes,
                                               SubjectMembership membership, AppIdResolver appIds,
                                               FeatureToggleGate toggles) {
        return new SnapshotAssembler(effectiveRoles, authorizations, menus, dataScopes, membership, appIds,
                toggles);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotRecomputeService snapshotRecomputeService(SnapshotRepository snapshots,
                                                             SnapshotAssembler assembler,
                                                             AuthzChangedEventPublisher publisher,
                                                             AffectedPersonResolver affectedPersonResolver) {
        return new SnapshotRecomputeService(snapshots, assembler, publisher, affectedPersonResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotApi snapshotApi(SnapshotRepository snapshots, SnapshotRecomputeService recomputeService) {
        return new SnapshotAppService(snapshots, recomputeService);
    }

    // ---------- BAS-27 子系统 / 模块 / 功能目录与功能开关 ----------

    @Bean
    @ConditionalOnMissingBean
    public SubsystemRepository subsystemRepository(SubsystemMapper mapper) {
        return new MybatisSubsystemRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModuleRepository moduleRepository(ModuleMapper mapper) {
        return new MybatisModuleRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FunctionRepository functionRepository(FunctionMapper mapper) {
        return new MybatisFunctionRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleRepository featureToggleRepository(FeatureToggleMapper mapper) {
        return new MybatisFeatureToggleRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgAdminScopeRepository orgAdminScopeRepository(OrgAdminScopeMapper mapper,
                                                           ObjectMapper objectMapper) {
        return new MybatisOrgAdminScopeRepository(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SelfManagedImportRepository selfManagedImportRepository(SelfManagedImportMapper batchMapper,
                                                                   SelfManagedImportItemMapper itemMapper) {
        return new MybatisSelfManagedImportRepository(batchMapper, itemMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleApi featureToggleApi(FeatureToggleRepository toggles, ModuleRepository modules,
                                             FunctionRepository functions, SubsystemProperties properties) {
        return new FeatureToggleService(toggles, modules, functions, properties);
    }

    /** 功能开关生效范围解析：授权快照与授权查询据此剔除停用范围内的菜单与权限码。 */
    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleGate featureToggleGate(FeatureToggleApi toggles, SubsystemRepository subsystems,
                                               ModuleRepository modules, FunctionRepository functions,
                                               MenuRepository menus) {
        return new FeatureToggleGate(toggles, subsystems, modules, functions, menus);
    }

    /** 子系统与目录服务：同一实例同时暴露 {@link SubsystemApi} 与 {@link DirectoryApi}。 */
    @Bean
    @ConditionalOnMissingBean(SubsystemDirectoryService.class)
    public SubsystemDirectoryService subsystemDirectoryService(SubsystemRepository subsystems,
                                                               ModuleRepository modules,
                                                               FunctionRepository functions,
                                                               SelfManagedImportRepository imports,
                                                               MenuRepository menus, FeatureToggleApi toggles,
                                                               AppApi apps, AppIdResolver appIds,
                                                               SubsystemProperties properties) {
        return new SubsystemDirectoryService(subsystems, modules, functions, imports, menus, toggles, apps,
                appIds, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SubsystemApi.class)
    public SubsystemApi subsystemApi(SubsystemDirectoryService service) {
        return service;
    }

    @Bean
    @ConditionalOnMissingBean(DirectoryApi.class)
    public DirectoryApi directoryApi(SubsystemDirectoryService service) {
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgAdminScopeApi orgAdminScopeApi(OrgAdminScopeRepository scopes, SubjectMembership membership) {
        return new OrgAdminScopeService(scopes, membership);
    }

    @Bean
    @ConditionalOnMissingBean
    public SelfManagedImportApi selfManagedImportApi(SubsystemRepository subsystems,
                                                     SelfManagedImportRepository imports,
                                                     RoleRepository roles, RoleGrantRepository grants,
                                                     AppIdResolver appIds, ObjectMapper objectMapper) {
        return new SelfManagedImporter(subsystems, imports, roles, grants, appIds, objectMapper);
    }

    // ---------- BAS-27 REST 控制器 ----------
    // 与上列 Bean 同生命周期注册：无持久化上下文（缺 SqlSessionFactory 等）时整块跳过，
    // 避免组件扫描出的控制器在依赖缺失时装配失败。

    @Bean
    @ConditionalOnMissingBean
    public SubsystemController subsystemController(SubsystemApi subsystems,
                                                   SelfManagedImportApi selfImports) {
        return new SubsystemController(subsystems, selfImports);
    }

    @Bean
    @ConditionalOnMissingBean
    public DirectoryController directoryController(DirectoryApi directories) {
        return new DirectoryController(directories);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleController featureToggleController(FeatureToggleApi toggles) {
        return new FeatureToggleController(toggles);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgAdminScopeController orgAdminScopeController(OrgAdminScopeApi scopes) {
        return new OrgAdminScopeController(scopes);
    }
}
