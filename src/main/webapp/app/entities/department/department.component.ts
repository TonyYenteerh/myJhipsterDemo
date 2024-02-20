import { Component, Vue, Inject } from 'vue-property-decorator';
import Vue2Filters from 'vue2-filters';
import { IDepartment } from '@/shared/model/department.model';

import DepartmentService from './department.service';
import AlertService from '@/shared/alert/alert.service';

@Component({
  mixins: [Vue2Filters.mixin],
})
export default class Department extends Vue {
  @Inject('departmentService') private departmentService: () => DepartmentService;
  @Inject('alertService') private alertService: () => AlertService;

  public currentSearch = '';
  private removeId: number = null;

  public departments: IDepartment[] = [];

  public isFetching = false;

  public mounted(): void {
    this.retrieveAllDepartments();
  }

  public search(query): void {
    if (!query) {
      return this.clear();
    }
    this.currentSearch = query;
    this.retrieveAllDepartments();
  }

  public clear(): void {
    this.currentSearch = '';
    this.retrieveAllDepartments();
  }

  public retrieveAllDepartments(): void {
    this.isFetching = true;
    if (this.currentSearch) {
      this.departmentService()
        .search(this.currentSearch)
        .then(
          res => {
            this.departments = res;
            this.isFetching = false;
          },
          err => {
            this.isFetching = false;
            this.alertService().showHttpError(this, err.response);
          }
        );
      return;
    }
    this.departmentService()
      .retrieve()
      .then(
        res => {
          this.departments = res.data;
          this.isFetching = false;
        },
        err => {
          this.isFetching = false;
          this.alertService().showHttpError(this, err.response);
        }
      );
  }

  public handleSyncList(): void {
    this.clear();
  }

  public prepareRemove(instance: IDepartment): void {
    this.removeId = instance.id;
    if (<any>this.$refs.removeEntity) {
      (<any>this.$refs.removeEntity).show();
    }
  }

  public removeDepartment(): void {
    this.departmentService()
      .delete(this.removeId)
      .then(() => {
        const message = this.$t('myJhipsterDemoApp.department.deleted', { param: this.removeId });
        this.$bvToast.toast(message.toString(), {
          toaster: 'b-toaster-top-center',
          title: 'Info',
          variant: 'danger',
          solid: true,
          autoHideDelay: 5000,
        });
        this.removeId = null;
        this.retrieveAllDepartments();
        this.closeDialog();
      })
      .catch(error => {
        this.alertService().showHttpError(this, error.response);
      });
  }

  public closeDialog(): void {
    (<any>this.$refs.removeEntity).hide();
  }
}
