import { Module } from 'vuex';

export const translationStore: Module<any, any> = {
  state: {
    currentLanguage: localStorage.getItem('currentLanguage') || 'zh-tw',
    languages: {
      'zh-tw': { name: '繁體中文' },
      en: { name: 'English' },
      ja: { name: '日本語' },
      // jhipster-needle-i18n-language-key-pipe - JHipster will add/remove languages in this object
    },
  },
  getters: {
    currentLanguage: state => state.currentLanguage,
    languages: state => state.languages,
  },
  mutations: {
    currentLanguage(state, newLanguage) {
      state.currentLanguage = newLanguage;
      localStorage.setItem('currentLanguage', newLanguage);
    },
  },
};
