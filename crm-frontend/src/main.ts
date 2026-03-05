import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Paginator from 'primevue/paginator'
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'
import Toast from 'primevue/toast'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Dialog from 'primevue/dialog'
import Drawer from 'primevue/drawer'
import ProgressSpinner from 'primevue/progressspinner'
import Textarea from 'primevue/textarea'
import DatePicker from 'primevue/datepicker'
import MultiSelect from 'primevue/multiselect'
import ToggleSwitch from 'primevue/toggleswitch'
import Badge from 'primevue/badge'
import Chip from 'primevue/chip'

import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import Aura from '@primevue/themes/aura'
import App from './App.vue'
import router from './router'
import '@/styles/main.css'
import 'primeicons/primeicons.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: { prefix: 'p', darkModeSelector: '[data-theme="dark"]', cssLayer: false }
  }
})
app.component('DataTable', DataTable)
app.component('Column', Column)
app.component('Button', Button)
app.component('InputText', InputText)
app.component('Select', Select)
app.component('Tag', Tag)
app.component('Paginator', Paginator)
app.component('ContextMenu', ContextMenu)
app.component('ConfirmDialog', ConfirmDialog)
app.component('Toast', Toast)
app.component('IconField', IconField)
app.component('InputIcon', InputIcon)
app.component('TabView', TabView)
app.component('TabPanel', TabPanel)
app.component('Dialog', Dialog)
app.component('Drawer', Drawer)
app.component('ProgressSpinner', ProgressSpinner)
app.component('Textarea', Textarea)
app.component('DatePicker', DatePicker)
app.component('MultiSelect', MultiSelect)
app.component('ToggleSwitch', ToggleSwitch)
app.component('Badge', Badge)
app.component('Chip', Chip)
app.use(ToastService)
app.use(ConfirmationService)
app.mount('#app')
