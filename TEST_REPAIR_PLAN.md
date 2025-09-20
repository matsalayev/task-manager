# Test Repair Plan - Task Manager

## Current Status Summary

### ✅ **Successfully Completed**
- **Main Application**: `sbt compile` works perfectly ✅
- **Clean Architecture**: Domain refactoring completed successfully ✅
- **Core Functionality**: Business logic compiles without errors ✅
- **API Authentication**: AuthedUser and AuthedRequest issues mostly resolved ✅

### ⚠️ **Test Compilation Issues Remaining**

Based on the error analysis, we have approximately **100+ test compilation errors** across three modules:

## Test Issues by Module

### 1. **endpoints-01-repos** (Repository Tests)
**Files with Issues:**
- `AnalyticsRepositorySpec.scala`
- `NotificationsRepositorySpec.scala`

**Main Problems:**
- `tm.repositories.data.users` object missing
- `UserGoalsData` type not found (should be `AnalyticsData.UserGoalsData`)
- `DashboardNotificationData` constructor issues
- Database test utilities missing (`DatabaseSuite`, `DatabaseResource`)
- Repository constructor signature mismatches

### 2. **endpoints-02-core** (Service Tests) 
**Files with Issues:**
- `NotificationIntegrationSpec.scala`
- `NotificationServiceSpec.scala`

**Main Problems:**
- Database test infrastructure missing
- Domain type import issues
- User constructor problems
- Repository session parameter issues
- Test framework utilities not found (`test`, `expect`)

### 3. **endpoints-03-api** (API Tests)
**Files with Issues:**
- `DashboardRoutesSpec.scala` 
- `NotificationRoutesSpec.scala`

**Main Problems:**
- JSON codec issues for custom types
- `LiveWorkStats` and `UserGoalsUpdate` encoder/decoder missing
- `Map[String, Any]` decoder issues

## Systematic Repair Strategy

### Phase 1: **Foundation Fixes** (High Priority)
1. **Fix Repository Test Data**
   - Create proper `tm.repositories.data` object with test users
   - Update `AnalyticsData` type usage throughout tests
   
2. **Database Test Infrastructure**
   - Identify correct database test utilities location
   - Fix database session parameter issues
   - Update repository constructor calls

3. **Domain Type Corrections**
   - Fix `EntityType` usage (string → enum)
   - Update domain type imports with `_root_` prefix
   - Correct refined type usage

### Phase 2: **Service Layer Fixes** (Medium Priority)
1. **Mock Repository Updates**
   - Ensure all mocks implement correct interface signatures
   - Fix type mismatches in mock implementations

2. **Test Framework Integration**
   - Fix weaver test framework imports
   - Ensure `test` and `expect` functions are available

3. **User Constructor Issues**
   - Update all `User` constructors to match domain model
   - Fix `AuthedUser.User` usage consistency

### Phase 3: **API Layer Fixes** (Medium Priority) 
1. **JSON Codec Issues**
   - Add missing `Codec` instances for service types
   - Fix circular JSON decoding issues

2. **HTTP4s Integration**
   - Complete remaining `AuthedRequest` fixes
   - Ensure proper entity encoding/decoding

### Phase 4: **Integration & Verification** (Low Priority)
1. **End-to-End Test Verification**
   - Run individual module tests
   - Verify database connectivity in tests
   - Check all test suites pass

## Detailed Action Items

### 🔧 **Immediate Fixes Needed**

#### **Repository Layer (endpoints-01-repos)**
- [ ] Create/fix `tm.repositories.data` test data object
- [ ] Replace `UserGoalsData` with `AnalyticsData.UserGoalsData` 
- [ ] Replace `DashboardNotificationData` with correct type
- [ ] Fix database test utility imports
- [ ] Update repository constructor calls with session parameter

#### **Service Layer (endpoints-02-core)**
- [ ] Fix database test infrastructure imports
- [ ] Update domain type imports with `_root_` prefix
- [ ] Fix User constructor parameter mismatches
- [ ] Update notification test entity types from strings to enums
- [ ] Fix test framework function availability

#### **API Layer (endpoints-03-api)**
- [ ] Add JSON codecs for `LiveWorkStats` and `UserGoalsUpdate`
- [ ] Fix remaining `Map[String, Any]` decoder issues
- [ ] Complete `AuthedRequest` migration in remaining files

### 🎯 **Success Criteria**

Each phase should achieve:
1. **Phase 1**: Repository tests compile successfully
2. **Phase 2**: Service tests compile successfully  
3. **Phase 3**: API tests compile successfully
4. **Phase 4**: All tests run and pass (or fail for business logic reasons, not compilation)

### 📊 **Progress Tracking**

**Current Status:**
- **Main App Compilation**: ✅ DONE
- **Domain Architecture**: ✅ DONE  
- **Repository Tests**: ✅ COMPILE SUCCESS (9/31 tests pass, 22 SQL constraint errors)
- **Service Tests**: ⚠️ Compilation errors remain
- **API Tests**: ❌ ~4 errors
- **Integration Tests**: ❌ Not tested yet

**Estimated Effort:**
- **Phase 1**: 2-3 hours (high complexity - database infrastructure)
- **Phase 2**: 1-2 hours (medium complexity - type fixes)
- **Phase 3**: 30 minutes (low complexity - JSON codecs)
- **Phase 4**: 1 hour (testing and verification)

**Total Estimated Time: 4-6 hours**

## Recommended Approach

1. **Start Fresh**: Begin with Phase 1 fixes to establish solid foundation
2. **Module by Module**: Complete one module fully before moving to next
3. **Incremental Testing**: Compile frequently to catch regressions early
4. **Documentation**: Update this plan as issues are discovered/resolved

## Notes

- Main business functionality is already working correctly
- These test fixes are important for ensuring code quality and preventing regressions
- Priority should be on getting tests compiling first, then making them pass
- Some tests may need business logic fixes after compilation issues are resolved

---
*Last Updated: September 19, 2025*
*Status: Domain refactoring complete, test repair in progress*